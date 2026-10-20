package xyz.byqy.vane.system.infrastructure;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 短信上行消息（JDBC）。 */
@Repository
public class SmsInboundRepository {

    private final JdbcTemplate jdbc;

    public SmsInboundRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void append(String providerCode, String providerMessageId, String phoneMasked, String phoneHash,
                       String content, Instant receivedAt, boolean processed, String matchedKeyword) {
        jdbc.update("""
                insert into sys_sms_inbound (provider_code, provider_message_id, phone_masked, phone_hash, content,
                                             received_at, processed, matched_keyword)
                values (?, ?, ?, ?, ?, ?, ?, ?)
                """, providerCode, providerMessageId, phoneMasked, phoneHash, content,
                receivedAt == null ? java.sql.Timestamp.from(Instant.now()) : java.sql.Timestamp.from(receivedAt),
                processed, matchedKeyword);
    }

    public PageResult<InboundRow> page(int pageNumber, int pageSize) {
        int size = Math.min(Math.max(pageSize, 1), 100);
        int number = Math.max(pageNumber, 0);
        Long total = jdbc.queryForObject("select count(*) from sys_sms_inbound", Long.class);
        List<InboundRow> items = jdbc.query("""
                select id, provider_code, provider_message_id, phone_masked, content, received_at, processed,
                       matched_keyword
                  from sys_sms_inbound
                 order by received_at desc
                 limit ? offset ?
                """, (rs, rowNum) -> new InboundRow(rs.getObject("id", UUID.class),
                rs.getString("provider_code"), rs.getString("provider_message_id"),
                rs.getString("phone_masked"), rs.getString("content"),
                rs.getTimestamp("received_at") == null ? null : rs.getTimestamp("received_at").toInstant(),
                rs.getBoolean("processed"), rs.getString("matched_keyword")), size, number * size);
        return new PageResult<>(items, new PageResult.PageMeta(number, size, total == null ? 0 : total));
    }

    public record InboundRow(UUID id, String providerCode, String providerMessageId, String phoneMasked,
                             String content, Instant receivedAt, boolean processed, String matchedKeyword) {
    }

    public record PageResult<T>(List<T> items, PageMeta page) {
        public record PageMeta(int number, int size, long total) {
        }
    }
}
