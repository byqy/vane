package xyz.byqy.vane.system.application;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import tools.jackson.databind.ObjectMapper;
import xyz.byqy.vane.system.infrastructure.SmsConfigCipher;
import xyz.byqy.vane.system.infrastructure.SmsLogRepository;
import xyz.byqy.vane.system.infrastructure.SmsLogRepository.ReceiptCandidate;
import xyz.byqy.vane.system.infrastructure.SmsProviderRepository;
import xyz.byqy.vane.system.infrastructure.SmsProviderRepository.SmsProviderRow;

/**
 * 回执轮询任务（ADR-022 P3）：对近 7 天未拿到终态回执的日志，按服务商能力调用
 * 阿里云 QuerySendDetails / 腾讯云 PullSmsSendStatus，追加回执。
 */
@Component
public class SmsReceiptPollHandler implements JobHandler {

    private static final Logger log = LoggerFactory.getLogger(SmsReceiptPollHandler.class);
    private static final String LOCK_KEY = "vane:sms:receipt:poll:lock";
    private static final int BATCH = 200;

    private final SmsLogRepository logs;
    private final SmsProviderRepository providers;
    private final SmsConfigCipher cipher;
    private final ObjectMapper json;
    private final SmsReceiptService receipts;
    private final StringRedisTemplate redis;
    private final Map<String, SmsReceiptPoller> pollers = new LinkedHashMap<>();

    public SmsReceiptPollHandler(SmsLogRepository logs, SmsProviderRepository providers, SmsConfigCipher cipher,
                                 ObjectMapper json, SmsReceiptService receipts, StringRedisTemplate redis,
                                 List<SmsReceiptPoller> pollers) {
        this.logs = logs;
        this.providers = providers;
        this.cipher = cipher;
        this.json = json;
        this.receipts = receipts;
        this.redis = redis;
        for (SmsReceiptPoller poller : pollers) {
            this.pollers.put(poller.providerType(), poller);
        }
    }

    @Override
    public String name() {
        return "sms-receipt-poll";
    }

    @Override
    public void run(String params) {
        if (!JobLocks.tryAcquire(redis, LOCK_KEY, Duration.ofMinutes(5))) {
            return;
        }
        try {
            Map<String, List<ReceiptCandidate>> byProvider = new LinkedHashMap<>();
            for (ReceiptCandidate candidate : logs.receiptCandidates(BATCH)) {
                byProvider.computeIfAbsent(candidate.providerCode(), key -> new ArrayList<>()).add(candidate);
            }
            int appended = 0;
            for (Map.Entry<String, List<ReceiptCandidate>> entry : byProvider.entrySet()) {
                appended += pollProvider(entry.getKey(), entry.getValue());
            }
            if (appended > 0 || !byProvider.isEmpty()) {
                log.info("SMS receipt poll: providers={}, candidates={}, appended={}",
                        byProvider.size(), byProvider.values().stream().mapToInt(List::size).sum(), appended);
            }
        } finally {
            JobLocks.release(redis, LOCK_KEY);
        }
    }

    private int pollProvider(String providerCode, List<ReceiptCandidate> candidates) {
        SmsProviderRow provider = providers.findByCode(providerCode).orElse(null);
        if (provider == null || !provider.enabled()) {
            return 0;
        }
        SmsReceiptPoller poller = pollers.get(provider.type());
        if (poller == null) {
            return 0;
        }
        Map<String, String> config;
        try {
            @SuppressWarnings("unchecked")
            Map<String, String> decrypted = json.readValue(
                    cipher.decrypt(provider.code(), provider.keyVersion(), provider.configCipher()), Map.class);
            config = decrypted;
        } catch (RuntimeException ex) {
            return 0;
        }
        List<SmsReceiptPoller.PollTarget> targets = new ArrayList<>();
        for (ReceiptCandidate candidate : candidates) {
            try {
                String phone = cipher.decryptData(candidate.phoneKeyVersion(), "sys_sms_log",
                        candidate.phoneCipher());
                targets.add(new SmsReceiptPoller.PollTarget(candidate.logId(), phone,
                        candidate.providerMessageId(), candidate.sentAt()));
            } catch (RuntimeException ex) {
                // 密钥轮换后旧版本缺失时跳过该条，等待人工重新加密
            }
        }
        if (targets.isEmpty()) {
            return 0;
        }
        int appended = 0;
        for (SmsReceiptPoller.PolledReceipt receipt : poller.poll(config, targets)) {
            if (receipts.recordFromProvider(providerCode, receipt.logId(), receipt.status(),
                    receipt.providerStatus(), receipt.occurredAt(), receipt.rawJson())) {
                appended++;
            }
        }
        return appended;
    }
}
