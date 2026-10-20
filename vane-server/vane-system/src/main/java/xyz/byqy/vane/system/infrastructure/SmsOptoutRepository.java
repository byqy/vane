package xyz.byqy.vane.system.infrastructure;

import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 短信退订名单（JDBC）：按 phone_hash + scope 唯一。 */
@Repository
public class SmsOptoutRepository {

    private final JdbcTemplate jdbc;

    public SmsOptoutRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean insertIfAbsent(String phoneHash, String scope, String source, String reason) {
        return jdbc.update("""
                insert into sys_sms_optout (phone_hash, scope, source, reason)
                values (?, ?, ?, ?)
                on conflict (phone_hash, scope) do nothing
                """, phoneHash, scope, source, reason) > 0;
    }

    public PageResult<OptoutRow> page(int pageNumber, int pageSize) {
        int size = Math.min(Math.max(pageSize, 1), 100);
        int number = Math.max(pageNumber, 0);
        Long total = jdbc.queryForObject("select count(*) from sys_sms_optout", Long.class);
        List<OptoutRow> items = jdbc.query("""
                select id, phone_hash, scope, source, reason, created_at
                  from sys_sms_optout
                 order by created_at desc
                 limit ? offset ?
                """, (rs, rowNum) -> new OptoutRow(rs.getObject("id", UUID.class), rs.getString("phone_hash"),
                rs.getString("scope"), rs.getString("source"), rs.getString("reason"),
                rs.getTimestamp("created_at") == null ? null : rs.getTimestamp("created_at").toInstant()),
                size, number * size);
        return new PageResult<>(items, new PageResult.PageMeta(number, size, total == null ? 0 : total));
    }

    public boolean delete(UUID id) {
        return jdbc.update("delete from sys_sms_optout where id = ?", id) > 0;
    }

    public record OptoutRow(UUID id, String phoneHash, String scope, String source, String reason,
                            java.time.Instant createdAt) {
    }

    public record PageResult<T>(List<T> items, PageMeta page) {
        public record PageMeta(int number, int size, long total) {
        }
    }
}
