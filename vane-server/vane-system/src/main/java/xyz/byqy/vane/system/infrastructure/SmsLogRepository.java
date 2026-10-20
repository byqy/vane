package xyz.byqy.vane.system.infrastructure;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 短信发送日志（JDBC）：只增不改；分页查询供后台查看。 */
@Repository
public class SmsLogRepository {

    private final JdbcTemplate jdbc;

    public SmsLogRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void append(SmsLogEntry entry) {
        jdbc.update("""
                insert into sys_sms_log (template_code, phone_masked, phone_hash, country_code, provider_code,
                                         provider_message_id, campaign_id, result, error_code, duration_ms, trace_id,
                                         phone_cipher, phone_key_version)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, entry.templateCode(), entry.phoneMasked(), entry.phoneHash(), entry.countryCode(),
                entry.providerCode(), entry.providerMessageId(), entry.campaignId(), entry.result(),
                entry.errorCode(), entry.durationMs(), entry.traceId(), entry.phoneCipher(),
                entry.phoneKeyVersion());
    }

    /** 回执轮询候选：近 7 天、有厂商消息 ID 与号码密文、尚未拿到终态回执。 */
    public List<ReceiptCandidate> receiptCandidates(int limit) {
        return jdbc.query("""
                select l.id, l.provider_code, l.provider_message_id, l.phone_cipher, l.phone_key_version,
                       l.created_at
                  from sys_sms_log l
                 where l.provider_message_id is not null
                   and l.phone_cipher is not null
                   and l.created_at > now() - interval '7 days'
                   and not exists (select 1 from sys_sms_receipt r
                                   where r.log_id = l.id and r.status in ('DELIVERED', 'FAILED'))
                 order by l.created_at desc
                 limit ?
                """, (rs, rowNum) -> new ReceiptCandidate(rs.getObject("id", UUID.class),
                rs.getString("provider_code"), rs.getString("provider_message_id"),
                rs.getString("phone_cipher"), rs.getString("phone_key_version"),
                rs.getTimestamp("created_at") == null ? null : rs.getTimestamp("created_at").toInstant()),
                limit);
    }

    public record ReceiptCandidate(UUID logId, String providerCode, String providerMessageId,
                                   String phoneCipher, String phoneKeyVersion, Instant sentAt) {
    }

    public PageResult<SmsLogView> page(String templateCode, String providerCode, String result, Instant from,
                                       Instant to, int pageNumber, int pageSize) {
        int size = Math.min(Math.max(pageSize, 1), 100);
        int number = Math.max(pageNumber, 0);
        StringBuilder where = new StringBuilder(" where 1 = 1");
        List<Object> args = new ArrayList<>();
        if (templateCode != null && !templateCode.isBlank()) {
            where.append(" and template_code = ?");
            args.add(templateCode);
        }
        if (providerCode != null && !providerCode.isBlank()) {
            where.append(" and provider_code = ?");
            args.add(providerCode);
        }
        if (result != null && !result.isBlank()) {
            where.append(" and result = ?");
            args.add(result);
        }
        if (from != null) {
            where.append(" and created_at >= ?");
            args.add(java.sql.Timestamp.from(from));
        }
        if (to != null) {
            where.append(" and created_at < ?");
            args.add(java.sql.Timestamp.from(to));
        }
        Long total = jdbc.queryForObject("select count(*) from sys_sms_log" + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(size);
        pageArgs.add(number * size);
        List<SmsLogView> items = jdbc.query("""
                select id, template_code, phone_masked, country_code, provider_code, provider_message_id,
                       campaign_id, result, error_code, duration_ms, trace_id, created_at,
                       (select r.status from sys_sms_receipt r where r.log_id = sys_sms_log.id
                         order by r.id desc limit 1) as delivery_status
                  from sys_sms_log
                """ + where + " order by created_at desc limit ? offset ?",
                (rs, rowNum) -> new SmsLogView(rs.getObject("id", UUID.class),
                        rs.getString("template_code"), rs.getString("phone_masked"),
                        rs.getString("country_code"), rs.getString("provider_code"),
                        rs.getString("provider_message_id"), rs.getObject("campaign_id", UUID.class),
                        rs.getString("result"), rs.getString("error_code"),
                        rs.getObject("duration_ms", Integer.class),
                        rs.getString("trace_id"),
                        rs.getTimestamp("created_at") == null ? null : rs.getTimestamp("created_at").toInstant(),
                        rs.getString("delivery_status")),
                pageArgs.toArray());
        return new PageResult<>(items, new PageResult.PageMeta(number, size, total == null ? 0 : total));
    }

    public record SmsLogEntry(String templateCode, String phoneMasked, String phoneHash, String countryCode,
                              String providerCode, String providerMessageId, UUID campaignId, String result,
                              String errorCode, Integer durationMs, String traceId, String phoneCipher,
                              String phoneKeyVersion) {
    }

    public record SmsLogView(UUID id, String templateCode, String phoneMasked, String countryCode,
                             String providerCode, String providerMessageId, UUID campaignId, String result,
                             String errorCode, Integer durationMs, String traceId, Instant createdAt,
                             String deliveryStatus) {
    }

    public record PageResult<T>(List<T> items, PageMeta page) {
        public record PageMeta(int number, int size, long total) {
        }
    }
}
