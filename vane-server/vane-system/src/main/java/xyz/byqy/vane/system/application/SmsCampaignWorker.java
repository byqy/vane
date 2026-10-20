package xyz.byqy.vane.system.application;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import tools.jackson.databind.ObjectMapper;
import xyz.byqy.vane.common.error.ApiException;
import xyz.byqy.vane.system.infrastructure.SmsCampaignRepository;
import xyz.byqy.vane.system.infrastructure.SmsCampaignRepository.CampaignRow;
import xyz.byqy.vane.system.infrastructure.SmsCampaignRepository.RecipientRow;

/**
 * 群发 worker（ADR-022 P2）：由定时任务触发，Valkey 锁保证单实例执行；
 * 按活动限速批量发送，发送前过滤退订，预算/日限额触顶自动暂停。
 */
@Component
public class SmsCampaignWorker implements JobHandler {

    private static final Logger log = LoggerFactory.getLogger(SmsCampaignWorker.class);
    private static final String LOCK_KEY = "vane:sms:campaign:lock";
    private static final Duration LOCK_TTL = Duration.ofMinutes(5);
    private static final int BATCH_SIZE = 50;
    private static final int MAX_CAMPAIGNS_PER_TICK = 10;
    private static final int MAX_BATCHES_PER_CAMPAIGN = 20;

    private final SmsCampaignRepository campaigns;
    private final SmsSendService smsSendService;
    private final StringRedisTemplate redis;
    private final ObjectMapper json;
    private final java.math.BigDecimal costPerMessage;

    public SmsCampaignWorker(SmsCampaignRepository campaigns, SmsSendService smsSendService,
                             StringRedisTemplate redis, ObjectMapper json,
                             @Value("${vane.sms.cost-per-message:0}") java.math.BigDecimal costPerMessage) {
        this.campaigns = campaigns;
        this.smsSendService = smsSendService;
        this.redis = redis;
        this.json = json;
        this.costPerMessage = costPerMessage;
    }

    @Override
    public String name() {
        return "sms-campaign";
    }

    @Override
    public void run(String params) {
        Boolean acquired = redis.opsForValue().setIfAbsent(LOCK_KEY, UUID.randomUUID().toString(), LOCK_TTL);
        if (!Boolean.TRUE.equals(acquired)) {
            return;
        }
        try {
            for (int i = 0; i < MAX_CAMPAIGNS_PER_TICK; i++) {
                CampaignRow campaign = campaigns.nextRunnable();
                if (campaign == null) {
                    return;
                }
                process(campaign);
            }
        } finally {
            redis.delete(LOCK_KEY);
        }
    }

    private void process(CampaignRow campaign) {
        if ("SCHEDULED".equals(campaign.status())) {
            campaigns.updateStatus(campaign.id(), "RUNNING");
        }
        for (int batch = 0; batch < MAX_BATCHES_PER_CAMPAIGN; batch++) {
            CampaignRow current = campaigns.find(campaign.id());
            if (current == null || !"RUNNING".equals(current.status())) {
                return;
            }
            if (limitReached(current)) {
                campaigns.updateStatus(campaign.id(), "PAUSED");
                log.warn("SMS campaign {} paused: budget or daily limit reached", campaign.id());
                return;
            }
            var pending = campaigns.pending(campaign.id(), BATCH_SIZE);
            if (pending.isEmpty()) {
                campaigns.refreshStats(campaign.id());
                campaigns.updateStatus(campaign.id(), "DONE");
                return;
            }
            for (RecipientRow recipient : pending) {
                sendOne(campaign, recipient);
            }
            campaigns.refreshStats(campaign.id());
            sleep(current.throttlePerSecond());
        }
    }

    private boolean limitReached(CampaignRow campaign) {
        if (campaign.dailyLimit() != null && campaign.dailyLimit() > 0
                && campaign.sent() + campaign.failed() >= campaign.dailyLimit()) {
            return true;
        }
        return campaign.budgetAmount() != null && costPerMessage.signum() > 0
                && costPerMessage.multiply(java.math.BigDecimal.valueOf(campaign.sent()))
                        .compareTo(campaign.budgetAmount()) >= 0;
    }

    private void sendOne(CampaignRow campaign, RecipientRow recipient) {
        if (recipient.phoneE164() == null) {
            return;
        }
        if (campaigns.isOptedOut(SmsCampaignService.sha256(recipient.phoneE164()))) {
            campaigns.markRecipient(recipient.id(), "OPTOUT", null, null, 0);
            return;
        }
        try {
            SmsSendService.SendOutcome outcome = smsSendService.send(campaign.templateCode(),
                    recipient.phoneE164(), campaign.locale(), params(recipient.paramsJson()), "CAMPAIGN",
                    campaign.id());
            campaigns.markRecipient(recipient.id(), "SENT", outcome.providerMessageId(), null, 1);
        } catch (ApiException ex) {
            campaigns.markRecipient(recipient.id(), "FAILED", null, ex.errorCode().code(), 1);
        } catch (RuntimeException ex) {
            campaigns.markRecipient(recipient.id(), "FAILED", null, "SMS.SEND.FAILED", 1);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> params(String paramsJson) {
        if (paramsJson == null || paramsJson.isBlank()) {
            return Map.of();
        }
        try {
            return json.readValue(paramsJson, Map.class);
        } catch (RuntimeException ex) {
            return Map.of();
        }
    }

    private void sleep(int throttlePerSecond) {
        long millis = Math.max(100, BATCH_SIZE * 1000L / Math.max(throttlePerSecond, 1));
        try {
            TimeUnit.MILLISECONDS.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
