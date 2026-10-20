package xyz.byqy.vane.system.infrastructure;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 短信送达回执（JDBC，只增）：每次状态变化一行，最新一行为当前状态。 */
@Repository
public class SmsReceiptRepository {

    private final JdbcTemplate jdbc;

    public SmsReceiptRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public UUID findLogId(String providerMessageId) {
        return jdbc.query("""
                select id from sys_sms_log where provider_message_id = ?
                 order by created_at desc limit 1
                """, (rs, rowNum) -> rs.getObject("id", UUID.class), providerMessageId)
                .stream().findFirst().orElse(null);
    }

    public boolean exists(UUID logId, String status) {
        Integer count = jdbc.queryForObject(
                "select count(*) from sys_sms_receipt where log_id = ? and status = ?",
                Integer.class, logId, status);
        return count != null && count > 0;
    }

    public void append(UUID logId, String providerMessageId, String status, String providerStatus,
                       Instant occurredAt, String rawJson) {
        jdbc.update("""
                insert into sys_sms_receipt (log_id, provider_message_id, status, provider_status, occurred_at, raw)
                values (?, ?, ?, ?, ?, ?::jsonb)
                """, logId, providerMessageId, status, providerStatus,
                occurredAt == null ? null : java.sql.Timestamp.from(occurredAt), rawJson);
    }

    public List<ReceiptRow> listByLog(UUID logId) {
        return jdbc.query("""
                select id, log_id, provider_message_id, status, provider_status, occurred_at, raw::text as raw,
                       created_at
                  from sys_sms_receipt where log_id = ?
                 order by id
                """, (rs, rowNum) -> new ReceiptRow(rs.getObject("id", UUID.class),
                rs.getObject("log_id", UUID.class), rs.getString("provider_message_id"),
                rs.getString("status"), rs.getString("provider_status"),
                rs.getTimestamp("occurred_at") == null ? null : rs.getTimestamp("occurred_at").toInstant(),
                rs.getString("raw"),
                rs.getTimestamp("created_at") == null ? null : rs.getTimestamp("created_at").toInstant()), logId);
    }

    public record ReceiptRow(UUID id, UUID logId, String providerMessageId, String status, String providerStatus,
                             Instant occurredAt, String rawJson, Instant createdAt) {
    }
}
