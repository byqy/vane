package xyz.byqy.vane.system.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import xyz.byqy.vane.common.error.ApiException;
import xyz.byqy.vane.common.error.CommonErrors;
import xyz.byqy.vane.system.infrastructure.MailLogRepository;
import xyz.byqy.vane.system.infrastructure.MailLogRepository.MailLogDetail;
import xyz.byqy.vane.system.infrastructure.SecretCipher;

/** 邮件发送日志查询（ADR-024）：按状态/模板/收件人/时间过滤；收件人按 HMAC 精确匹配。 */
@Service
public class MailLogQueryService {

    private static final int EXPORT_LIMIT = 5000;

    private final MailLogRepository logs;
    private final SecretCipher cipher;

    public MailLogQueryService(MailLogRepository logs, SecretCipher cipher) {
        this.logs = logs;
        this.cipher = cipher;
    }

    @Transactional(readOnly = true)
    public Page page(String status, String templateCode, String recipient, Instant timeFrom, Instant timeTo,
                     int pageNumber, int pageSize) {
        int size = Math.min(Math.max(pageSize, 1), 100);
        int number = Math.max(pageNumber, 0);
        String toHash = null;
        String toDomain = null;
        if (recipient != null && !recipient.isBlank()) {
            String value = recipient.strip();
            if (value.startsWith("@")) {
                toDomain = value.substring(1).toLowerCase();
            } else if (value.contains("@")) {
                if (!cipher.configured()) {
                    throw new ApiException(CommonErrors.MAIL_NOT_CONFIGURED);
                }
                toHash = cipher.hash(value.toLowerCase());
            } else {
                toDomain = value.toLowerCase();
            }
        }
        List<MailLogDetail> items = logs.page(status, templateCode, toHash, toDomain, timeFrom, timeTo,
                number * size, size);
        long total = logs.count(status, templateCode, toHash, toDomain, timeFrom, timeTo);
        return new Page(items, new PageMeta(number, size, total));
    }

    @Transactional(readOnly = true)
    public MailLogDetail detail(UUID id) {
        return logs.findById(id).orElseThrow(() -> new ApiException(CommonErrors.REQUEST_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public List<MailLogDetail> exportRows(String status, String templateCode, String recipient,
                                          Instant timeFrom, Instant timeTo) {
        Page page = page(status, templateCode, recipient, timeFrom, timeTo, 0, EXPORT_LIMIT);
        return page.items();
    }

    public record Page(List<MailLogDetail> items, PageMeta page) {
    }

    public record PageMeta(int number, int size, long total) {
    }
}
