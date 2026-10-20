package xyz.byqy.vane.system.application;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 厂商回执轮询适配器（ADR-022 P3）。 */
public interface SmsReceiptPoller {

    /** 对应 sys_sms_provider.provider_type。 */
    String providerType();

    List<PolledReceipt> poll(Map<String, String> config, List<PollTarget> targets);

    record PollTarget(UUID logId, String phoneE164, String providerMessageId, Instant sentAt) {
    }

    record PolledReceipt(UUID logId, String status, String providerStatus, Instant occurredAt, String rawJson) {
    }
}
