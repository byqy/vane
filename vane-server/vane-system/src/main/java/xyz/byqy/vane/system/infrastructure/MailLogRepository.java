package xyz.byqy.vane.system.infrastructure;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 邮件发送日志与队列（JDBC，ADR-024）：排队行可更新，终态保留摘要。 */
@Repository
public class MailLogRepository {

    private final JdbcTemplate jdbc;

    public MailLogRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void enqueue(MailQueueEntry entry) {
        jdbc.update("""
                insert into sys_mail_log (id, provider_code, template_code, locale, to_masked, to_domain,
                                          to_cipher, to_key_version, to_hash, variables, status, attempts)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, cast(? as jsonb), 'QUEUED', 0)
                """, entry.id(), entry.providerCode(), entry.templateCode(), entry.locale(),
                entry.toMasked(), entry.toDomain(), entry.toCipher(), entry.toKeyVersion(), entry.toHash(),
                entry.variablesJson());
    }

    /** UTC 当日已成功发送数（限额判断）。 */
    public int countSentToday(String providerCode) {
        Integer count = jdbc.queryForObject("""
                select count(*) from sys_mail_log
                 where provider_code = ?
                   and status = 'SENT'
                   and sent_at >= date_trunc('day', now() at time zone 'UTC') at time zone 'UTC'
                """, Integer.class, providerCode);
        return count == null ? 0 : count;
    }

    /** 取待发送队列：QUEUED 且到期。 */
    public List<MailQueueRow> claim(int limit) {
        return jdbc.query("""
                select id, provider_code, template_code, locale, to_cipher, to_key_version,
                       variables::text as variables, attempts
                  from sys_mail_log
                 where status = 'QUEUED'
                   and (next_attempt_at is null or next_attempt_at <= now())
                 order by created_at
                 limit ?
                """, (rs, rowNum) -> new MailQueueRow(rs.getObject("id", UUID.class),
                rs.getString("provider_code"), rs.getString("template_code"), rs.getString("locale"),
                rs.getString("to_cipher"), rs.getString("to_key_version"), rs.getString("variables"),
                rs.getInt("attempts")), limit);
    }

    public void markSent(UUID id, String subject) {
        jdbc.update("""
                update sys_mail_log
                   set status = 'SENT', subject = ?, sent_at = now(), attempts = attempts + 1,
                       error_code = null, error_detail = null, variables = null
                 where id = ?
                """, subject, id);
    }

    public void markRetry(UUID id, int attempts, Instant nextAttemptAt, String errorCode, String errorDetail) {
        jdbc.update("""
                update sys_mail_log
                   set status = 'QUEUED', attempts = ?, next_attempt_at = ?, error_code = ?, error_detail = ?
                 where id = ?
                """, attempts, java.sql.Timestamp.from(nextAttemptAt), errorCode, truncate(errorDetail), id);
    }

    public void markFailed(UUID id, int attempts, String errorCode, String errorDetail) {
        jdbc.update("""
                update sys_mail_log
                   set status = 'FAILED', attempts = ?, error_code = ?, error_detail = ?,
                       sent_at = now(), variables = null
                 where id = ?
                """, attempts, errorCode, truncate(errorDetail), id);
    }

    public Optional<MailLogDetail> findById(UUID id) {
        return jdbc.query("""
                select id, provider_code, template_code, locale, to_masked, to_domain, subject, status,
                       error_code, error_detail, attempts, duration_ms, trace_id, created_at, sent_at
                  from sys_mail_log where id = ?
                """, (rs, rowNum) -> detail(rs), id).stream().findFirst();
    }

    public List<MailLogDetail> page(String status, String templateCode, String toHash, String toDomain,
                                    Instant from, Instant to, int offset, int limit) {
        StringBuilder sql = new StringBuilder("""
                select id, provider_code, template_code, locale, to_masked, to_domain, subject, status,
                       error_code, error_detail, attempts, duration_ms, trace_id, created_at, sent_at
                  from sys_mail_log where 1 = 1
                """);
        List<Object> args = new ArrayList<>();
        appendFilters(sql, args, status, templateCode, toHash, toDomain, from, to);
        sql.append(" order by created_at desc limit ? offset ?");
        args.add(limit);
        args.add(offset);
        return jdbc.query(sql.toString(), (rs, rowNum) -> detail(rs), args.toArray());
    }

    public long count(String status, String templateCode, String toHash, String toDomain,
                      Instant from, Instant to) {
        StringBuilder sql = new StringBuilder("select count(*) from sys_mail_log where 1 = 1");
        List<Object> args = new ArrayList<>();
        appendFilters(sql, args, status, templateCode, toHash, toDomain, from, to);
        Long count = jdbc.queryForObject(sql.toString(), Long.class, args.toArray());
        return count == null ? 0 : count;
    }

    public List<MailLogDetail> exportRows(String status, String templateCode, String toHash, String toDomain,
                                         Instant from, Instant to, int limit) {
        return page(status, templateCode, toHash, toDomain, from, to, 0, limit);
    }

    public int deleteOlderThan(int days) {
        return jdbc.update("""
                delete from sys_mail_log where created_at < now() - make_interval(days => ?)
                """, days);
    }

    private static void appendFilters(StringBuilder sql, List<Object> args, String status, String templateCode,
                                      String toHash, String toDomain, Instant from, Instant to) {
        if (status != null && !status.isBlank()) {
            sql.append(" and status = ?");
            args.add(status);
        }
        if (templateCode != null && !templateCode.isBlank()) {
            sql.append(" and template_code = ?");
            args.add(templateCode);
        }
        if (toHash != null && !toHash.isBlank()) {
            sql.append(" and to_hash = ?");
            args.add(toHash);
        }
        if (toDomain != null && !toDomain.isBlank()) {
            sql.append(" and to_domain = ?");
            args.add(toDomain);
        }
        if (from != null) {
            sql.append(" and created_at >= ?");
            args.add(java.sql.Timestamp.from(from));
        }
        if (to != null) {
            sql.append(" and created_at <= ?");
            args.add(java.sql.Timestamp.from(to));
        }
    }

    private static MailLogDetail detail(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new MailLogDetail(rs.getObject("id", UUID.class), rs.getString("provider_code"),
                rs.getString("template_code"), rs.getString("locale"), rs.getString("to_masked"),
                rs.getString("to_domain"), rs.getString("subject"), rs.getString("status"),
                rs.getString("error_code"), rs.getString("error_detail"), rs.getInt("attempts"),
                (Integer) rs.getObject("duration_ms"), rs.getString("trace_id"),
                rs.getTimestamp("created_at") == null ? null : rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("sent_at") == null ? null : rs.getTimestamp("sent_at").toInstant());
    }

    private static String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 500 ? value : value.substring(0, 500);
    }

    public record MailQueueEntry(UUID id, String providerCode, String templateCode, String locale,
                                 String toMasked, String toDomain, String toCipher, String toKeyVersion,
                                 String toHash, String variablesJson) {
    }

    public record MailQueueRow(UUID id, String providerCode, String templateCode, String locale,
                               String toCipher, String toKeyVersion, String variablesJson, int attempts) {
    }

    public record MailLogDetail(UUID id, String providerCode, String templateCode, String locale,
                                String toMasked, String toDomain, String subject, String status,
                                String errorCode, String errorDetail, int attempts, Integer durationMs,
                                String traceId, Instant createdAt, Instant sentAt) {
    }
}
