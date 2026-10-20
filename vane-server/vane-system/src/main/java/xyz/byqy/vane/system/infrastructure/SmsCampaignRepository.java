package xyz.byqy.vane.system.infrastructure;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 短信群发活动与收件人（JDBC，ADR-022 P2）。 */
@Repository
public class SmsCampaignRepository {

    private final JdbcTemplate jdbc;

    public SmsCampaignRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public PageResult<CampaignRow> page(int pageNumber, int pageSize) {
        int size = Math.min(Math.max(pageSize, 1), 100);
        int number = Math.max(pageNumber, 0);
        Long total = jdbc.queryForObject("select count(*) from sys_sms_campaign", Long.class);
        List<CampaignRow> items = jdbc.query("""
                select c.id, c.name, c.template_id, t.code as template_code, c.provider_code, c.signature_id,
                       c.status, c.scheduled_at, c.throttle_per_second, c.daily_limit, c.budget_amount,
                       c.locale, c.total, c.sent, c.failed, c.created_at
                  from sys_sms_campaign c
                  join sys_message_template t on t.id = c.template_id
                 order by c.created_at desc
                 limit ? offset ?
                """, (rs, rowNum) -> row(rs), size, number * size);
        return new PageResult<>(items, new PageResult.PageMeta(number, size, total == null ? 0 : total));
    }

    public CampaignRow require(UUID id) {
        return jdbc.query("""
                select c.id, c.name, c.template_id, t.code as template_code, c.provider_code, c.signature_id,
                       c.status, c.scheduled_at, c.throttle_per_second, c.daily_limit, c.budget_amount,
                       c.locale, c.total, c.sent, c.failed, c.created_at
                  from sys_sms_campaign c
                  join sys_message_template t on t.id = c.template_id
                 where c.id = ?
                """, (rs, rowNum) -> row(rs), id).stream().findFirst().orElse(null);
    }

    public CampaignRow find(UUID id) {
        return require(id);
    }

    public UUID insert(String name, UUID templateId, String providerCode, UUID signatureId, java.time.Instant scheduledAt,
                       String status, Integer throttlePerSecond, Integer dailyLimit, java.math.BigDecimal budgetAmount,
                       String locale) {
        return jdbc.queryForObject("""
                insert into sys_sms_campaign (name, template_id, provider_code, signature_id, status, scheduled_at,
                                              throttle_per_second, daily_limit, budget_amount, locale)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                returning id
                """, UUID.class, name, templateId, providerCode, signatureId, status,
                scheduledAt == null ? null : java.sql.Timestamp.from(scheduledAt), throttlePerSecond,
                dailyLimit, budgetAmount, locale);
    }

    public void updateDraft(UUID id, String name, UUID templateId, String providerCode, UUID signatureId,
                            Instant scheduledAt, Integer throttlePerSecond, Integer dailyLimit,
                            java.math.BigDecimal budgetAmount, String locale) {
        jdbc.update("""
                update sys_sms_campaign
                   set name = ?, template_id = ?, provider_code = ?, signature_id = ?, scheduled_at = ?,
                       throttle_per_second = ?, daily_limit = ?, budget_amount = ?, locale = ?, updated_at = now()
                 where id = ? and status = 'DRAFT'
                """, name, templateId, providerCode, signatureId,
                scheduledAt == null ? null : java.sql.Timestamp.from(scheduledAt), throttlePerSecond,
                dailyLimit, budgetAmount, locale, id);
    }

    public void setScheduledNow(UUID id) {
        jdbc.update("update sys_sms_campaign set status = 'SCHEDULED', scheduled_at = now(), updated_at = now() where id = ?", id);
    }

    public void updateStatus(UUID id, String status) {
        jdbc.update("update sys_sms_campaign set status = ?, updated_at = now() where id = ?", status, id);
    }

    public CampaignRow nextRunnable() {
        return jdbc.query("""
                select c.id, c.name, c.template_id, t.code as template_code, c.provider_code, c.signature_id,
                       c.status, c.scheduled_at, c.throttle_per_second, c.daily_limit, c.budget_amount,
                       c.locale, c.total, c.sent, c.failed, c.created_at
                  from sys_sms_campaign c
                  join sys_message_template t on t.id = c.template_id
                 where c.status in ('SCHEDULED', 'RUNNING')
                   and (c.scheduled_at is null or c.scheduled_at <= now())
                 order by c.created_at
                 limit 1
                """, (rs, rowNum) -> row(rs)).stream().findFirst().orElse(null);
    }

    public List<RecipientRow> pending(UUID campaignId, int limit, List<String> countries) {
        if (countries != null && countries.isEmpty()) {
            return List.of();
        }
        String sql = """
                select id, phone_e164, country_code, params::text as params, status
                  from sys_sms_recipient
                 where campaign_id = ? and status = 'PENDING'
                """
                + (countries == null ? "" : " and country_code = any(?::text[])")
                + " order by id limit ?";
        org.springframework.jdbc.core.RowMapper<RecipientRow> mapper = (rs, rowNum) -> new RecipientRow(
                rs.getObject("id", UUID.class), rs.getString("phone_e164"), rs.getString("country_code"),
                rs.getString("params"), rs.getString("status"));
        if (countries == null) {
            return jdbc.query(sql, mapper, campaignId, limit);
        }
        return jdbc.query(sql, mapper, campaignId, countries.toArray(String[]::new), limit);
    }

    /** 按服务商/结果统计发送量（用于成本与运营看板）。 */
    public List<ProviderCount> stats(java.time.Instant from, java.time.Instant to) {
        return jdbc.query("""
                select provider_code, result, count(*) as cnt
                  from sys_sms_log
                 where created_at >= ? and created_at < ?
                 group by provider_code, result
                """, (rs, rowNum) -> new ProviderCount(rs.getString("provider_code"),
                rs.getString("result"), rs.getLong("cnt")),
                java.sql.Timestamp.from(from), java.sql.Timestamp.from(to));
    }

    public record ProviderCount(String providerCode, String result, long count) {
    }

    public boolean isOptedOut(String phoneHash) {
        Integer exists = jdbc.queryForObject("""
                select count(*) from sys_sms_optout where phone_hash = ? and scope in ('ALL', 'MARKETING')
                """, Integer.class, phoneHash);
        return exists != null && exists > 0;
    }

    public void markRecipient(UUID id, String status, String providerMessageId, String errorCode, int attempts) {
        jdbc.update("""
                update sys_sms_recipient
                   set status = ?, provider_message_id = ?, error_code = ?, attempts = attempts + ?,
                       sent_at = case when ? = 'SENT' then now() else sent_at end
                 where id = ?
                """, status, providerMessageId, errorCode, attempts, status, id);
    }

    public void refreshStats(UUID campaignId) {
        jdbc.update("""
                update sys_sms_campaign c
                   set total = (select count(*) from sys_sms_recipient r where r.campaign_id = c.id),
                       sent = (select count(*) from sys_sms_recipient r where r.campaign_id = c.id and r.status = 'SENT'),
                       failed = (select count(*) from sys_sms_recipient r where r.campaign_id = c.id and r.status = 'FAILED'),
                       updated_at = now()
                 where c.id = ?
                """, campaignId);
    }

    public boolean hasPending(UUID campaignId) {
        Integer count = jdbc.queryForObject(
                "select count(*) from sys_sms_recipient where campaign_id = ? and status = 'PENDING'",
                Integer.class, campaignId);
        return count != null && count > 0;
    }

    public void insertRecipients(UUID campaignId, List<RecipientInsert> rows) {
        jdbc.batchUpdate("""
                insert into sys_sms_recipient (campaign_id, phone_e164, country_code, params, source_line, status,
                                               error_code)
                values (?, ?, ?, ?::jsonb, ?, ?, ?)
                """, rows, 200, (ps, row) -> {
            ps.setObject(1, campaignId);
            ps.setString(2, row.phoneE164());
            ps.setString(3, row.countryCode());
            ps.setString(4, row.paramsJson() == null ? "{}" : row.paramsJson());
            if (row.sourceLine() == null) {
                ps.setNull(5, java.sql.Types.INTEGER);
            } else {
                ps.setInt(5, row.sourceLine());
            }
            ps.setString(6, row.status());
            ps.setString(7, row.errorCode());
        });
    }

    public boolean recipientExists(UUID campaignId, String phoneE164) {
        Integer count = jdbc.queryForObject("""
                select count(*) from sys_sms_recipient where campaign_id = ? and phone_e164 = ?
                """, Integer.class, campaignId, phoneE164);
        return count != null && count > 0;
    }

    public PageResult<RecipientRow> recipients(UUID campaignId, int pageNumber, int pageSize) {
        int size = Math.min(Math.max(pageSize, 1), 100);
        int number = Math.max(pageNumber, 0);
        Long total = jdbc.queryForObject("select count(*) from sys_sms_recipient where campaign_id = ?",
                Long.class, campaignId);
        List<RecipientRow> items = jdbc.query("""
                select id, phone_e164, country_code, params::text as params, status, error_code, attempts, sent_at
                  from sys_sms_recipient where campaign_id = ?
                 order by id limit ? offset ?
                """, (rs, rowNum) -> new RecipientRow(rs.getObject("id", UUID.class),
                rs.getString("phone_e164"), rs.getString("country_code"), rs.getString("params"),
                rs.getString("status"), rs.getString("error_code"), rs.getInt("attempts"),
                rs.getTimestamp("sent_at") == null ? null : rs.getTimestamp("sent_at").toInstant()),
                campaignId, size, number * size);
        return new PageResult<>(items, new PageResult.PageMeta(number, size, total == null ? 0 : total));
    }

    public List<RecipientRow> invalidRecipients(UUID campaignId) {
        return jdbc.query("""
                select id, phone_e164, country_code, params::text as params, status, error_code
                  from sys_sms_recipient where campaign_id = ? and status = 'INVALID'
                 order by source_line
                """, (rs, rowNum) -> new RecipientRow(rs.getObject("id", UUID.class),
                rs.getString("phone_e164"), rs.getString("country_code"), rs.getString("params"),
                rs.getString("status"), rs.getString("error_code"), 0, null), campaignId);
    }

    private static CampaignRow row(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new CampaignRow(rs.getObject("id", UUID.class), rs.getString("name"),
                rs.getObject("template_id", UUID.class), rs.getString("template_code"),
                rs.getString("provider_code"), rs.getObject("signature_id", UUID.class),
                rs.getString("status"),
                rs.getTimestamp("scheduled_at") == null ? null : rs.getTimestamp("scheduled_at").toInstant(),
                rs.getInt("throttle_per_second"),
                rs.getObject("daily_limit", Integer.class),
                rs.getBigDecimal("budget_amount"), rs.getString("locale"),
                rs.getInt("total"), rs.getInt("sent"), rs.getInt("failed"),
                rs.getTimestamp("created_at") == null ? null : rs.getTimestamp("created_at").toInstant());
    }

    public record CampaignRow(UUID id, String name, UUID templateId, String templateCode, String providerCode,
                              UUID signatureId, String status, Instant scheduledAt, int throttlePerSecond,
                              Integer dailyLimit, java.math.BigDecimal budgetAmount, String locale,
                              int total, int sent, int failed, Instant createdAt) {
    }

    public record RecipientRow(UUID id, String phoneE164, String countryCode, String paramsJson, String status,
                               String errorCode, int attempts, Instant sentAt) {
        public RecipientRow(UUID id, String phoneE164, String countryCode, String paramsJson, String status) {
            this(id, phoneE164, countryCode, paramsJson, status, null, 0, null);
        }
    }

    public record RecipientInsert(String phoneE164, String countryCode, String paramsJson, Integer sourceLine,
                                  String status, String errorCode) {
    }

    public record PageResult<T>(List<T> items, PageMeta page) {
        public record PageMeta(int number, int size, long total) {
        }
    }
}
