package xyz.byqy.vane.system.application;

import java.time.Instant;
import java.util.UUID;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.ObjectMapper;
import xyz.byqy.vane.common.error.ApiException;
import xyz.byqy.vane.common.error.CommonErrors;
import xyz.byqy.vane.system.infrastructure.SmsReceiptRepository;

/** 短信送达回执（ADR-022 P3）：追加式写入，同状态幂等。 */
@Service
public class SmsReceiptService {

    private static final List<String> DELIVERED = List.of("DELIVERED", "DELIVRD", "DELIVERY", "OK", "SUCCESS");
    private static final List<String> FAILED = List.of("FAILED", "UNDELIV", "FAIL", "REJECTED", "EXPIRED");

    private final SmsReceiptRepository receipts;
    private final SmsCallbackAuth auth;
    private final ObjectMapper json;

    public SmsReceiptService(SmsReceiptRepository receipts, SmsCallbackAuth auth, ObjectMapper json) {
        this.receipts = receipts;
        this.auth = auth;
        this.json = json;
    }

    /** 返回是否写入了新的回执（未知消息 ID 或重复状态返回 false）。 */
    @Transactional
    public boolean record(String providerCode, String token, SmsCallbackAuth.CallbackPayload payload) {
        if (!auth.verify(providerCode, token)) {
            throw new ApiException(CommonErrors.AUTH_FORBIDDEN);
        }
        if (payload.providerMessageId() == null || payload.providerMessageId().isBlank()) {
            throw new ApiException(CommonErrors.VALIDATION_FAILED);
        }
        var logId = receipts.findLogId(payload.providerMessageId());
        if (logId == null) {
            return false;
        }
        String status = mapStatus(payload.status(), payload.providerStatus());
        if (receipts.exists(logId, status)) {
            return false;
        }
        receipts.append(logId, payload.providerMessageId(), status, payload.providerStatus(),
                payload.occurredAt() == null ? Instant.now() : payload.occurredAt(),
                payload.raw() == null ? "{}" : json.writeValueAsString(payload.raw()));
        return true;
    }

    /** 轮询任务使用：已确定 logId，跳过回调鉴权。 */
    @Transactional
    public boolean recordFromProvider(String providerCode, UUID logId, String status, String providerStatus,
                                      Instant occurredAt, String rawJson) {
        if (receipts.exists(logId, status)) {
            return false;
        }
        receipts.append(logId, null, status, providerStatus,
                occurredAt == null ? Instant.now() : occurredAt, rawJson == null ? "{}" : rawJson);
        return true;
    }

    @Transactional(readOnly = true)
    public List<SmsReceiptRepository.ReceiptRow> listByLog(UUID logId) {
        return receipts.listByLog(logId);
    }

    static String mapStatus(String status, String providerStatus) {
        String normalized = (status != null ? status : providerStatus);
        if (normalized == null) {
            return "UNKNOWN";
        }
        String upper = normalized.strip().toUpperCase();
        if (List.of("PENDING", "DELIVERED", "FAILED", "UNKNOWN").contains(upper)) {
            return upper;
        }
        if (DELIVERED.contains(upper)) {
            return "DELIVERED";
        }
        if (FAILED.contains(upper)) {
            return "FAILED";
        }
        return "UNKNOWN";
    }
}
