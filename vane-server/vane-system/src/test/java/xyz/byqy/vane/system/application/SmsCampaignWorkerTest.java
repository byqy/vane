package xyz.byqy.vane.system.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import tools.jackson.databind.json.JsonMapper;
import xyz.byqy.vane.system.infrastructure.SmsCampaignRepository;
import xyz.byqy.vane.system.infrastructure.SmsCampaignRepository.CampaignRow;
import xyz.byqy.vane.system.infrastructure.SmsCampaignRepository.RecipientRow;

class SmsCampaignWorkerTest {

    private final SmsCampaignRepository campaigns = mock(SmsCampaignRepository.class);
    private final SmsSendService smsSendService = mock(SmsSendService.class);
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> valueOps = mock(ValueOperations.class);

    private final SmsCampaignWorker worker = new SmsCampaignWorker(campaigns, smsSendService, redis,
            JsonMapper.builder().build(), new SmsSendWindow(
                    java.time.Clock.fixed(Instant.parse("2026-10-20T04:00:00Z"), java.time.ZoneOffset.UTC),
                    false, 8, 21), new BigDecimal("0.05"));

    private final UUID campaignId = UUID.randomUUID();
    private final UUID optedOutId = UUID.randomUUID();
    private final UUID normalId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
    }

    private CampaignRow campaign(int sent, int failed, BigDecimal budget, Integer dailyLimit) {
        return new CampaignRow(campaignId, "活动", UUID.randomUUID(), "password.reset", "aliyun-cn", null,
                "RUNNING", null, 1000, dailyLimit, budget, "zh-CN", 2, sent, failed, Instant.now());
    }

    @Test
    void processesBatchAndMarksDone() {
        CampaignRow running = campaign(0, 0, null, null);
        when(campaigns.nextRunnable()).thenReturn(running, (CampaignRow) null);
        when(campaigns.find(campaignId)).thenReturn(running);
        when(campaigns.pending(campaignId, 50, null)).thenReturn(
                List.of(new RecipientRow(optedOutId, "+8613900139000", "CN", "{}", "PENDING"),
                        new RecipientRow(normalId, "+8613800138000", "CN", "{\"code\":\"1\"}", "PENDING")),
                List.of());
        when(campaigns.isOptedOut(SmsCampaignService.sha256("+8613900139000"))).thenReturn(true);
        when(campaigns.isOptedOut(SmsCampaignService.sha256("+8613800138000"))).thenReturn(false);
        when(smsSendService.send(eq("password.reset"), eq("+8613800138000"), eq("zh-CN"), any(), eq("CAMPAIGN"),
                eq(campaignId)))
                .thenReturn(new SmsSendService.SendOutcome("aliyun-cn", "msg-1", "+8613****8000", "OK"));

        worker.run(null);

        verify(campaigns).markRecipient(optedOutId, "OPTOUT", null, null, 0);
        verify(campaigns).markRecipient(normalId, "SENT", "msg-1", null, 1);
        verify(campaigns).updateStatus(campaignId, "DONE");
        verify(redis).delete("vane:sms:campaign:lock");
    }

    @Test
    void pausesCampaignWhenBudgetReached() {
        CampaignRow running = campaign(10, 0, new BigDecimal("0.50"), null);
        when(campaigns.nextRunnable()).thenReturn(running, (CampaignRow) null);
        when(campaigns.find(campaignId)).thenReturn(running);

        worker.run(null);

        verify(campaigns).updateStatus(campaignId, "PAUSED");
        verify(campaigns, never()).pending(any(), anyInt(), isNull());
    }

    @Test
    void pausesCampaignWhenDailyLimitReached() {
        CampaignRow running = campaign(5, 5, null, 10);
        when(campaigns.nextRunnable()).thenReturn(running, (CampaignRow) null);
        when(campaigns.find(campaignId)).thenReturn(running);

        worker.run(null);

        verify(campaigns).updateStatus(campaignId, "PAUSED");
    }

    @Test
    void marksRecipientFailedWhenSendThrows() {
        CampaignRow running = campaign(0, 0, null, null);
        when(campaigns.nextRunnable()).thenReturn(running, (CampaignRow) null);
        when(campaigns.find(campaignId)).thenReturn(running);
        when(campaigns.pending(campaignId, 50, null)).thenReturn(
                List.of(new RecipientRow(normalId, "+8613800138000", "CN", "{}", "PENDING")), List.of());
        when(campaigns.isOptedOut(anyString())).thenReturn(false);
        when(smsSendService.send(anyString(), anyString(), anyString(), any(), anyString(), any()))
                .thenThrow(new xyz.byqy.vane.common.error.ApiException(
                        xyz.byqy.vane.common.error.CommonErrors.SMS_SEND_FAILED));

        worker.run(null);

        verify(campaigns).markRecipient(normalId, "FAILED", null, "SMS.SEND.FAILED", 1);
    }

    @Test
    void skipsWhenAnotherInstanceHoldsLock() {
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(false);

        worker.run(null);

        verify(campaigns, never()).nextRunnable();
        verify(redis, never()).delete("vane:sms:campaign:lock");
    }

    @Test
    void transitionsScheduledCampaignToRunning() {
        CampaignRow scheduled = new CampaignRow(campaignId, "活动", UUID.randomUUID(), "password.reset",
                "aliyun-cn", null, "SCHEDULED", Instant.now(), 1000, null, null, "zh-CN", 0, 0, 0, Instant.now());
        CampaignRow running = campaign(0, 0, null, null);
        when(campaigns.nextRunnable()).thenReturn(scheduled, (CampaignRow) null);
        when(campaigns.find(campaignId)).thenReturn(running);
        when(campaigns.pending(campaignId, 50, null)).thenReturn(List.of());

        worker.run(null);

        verify(campaigns).updateStatus(campaignId, "RUNNING");
        verify(campaigns).updateStatus(campaignId, "DONE");
    }
}
