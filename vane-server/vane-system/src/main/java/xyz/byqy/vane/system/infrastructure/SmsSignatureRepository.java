package xyz.byqy.vane.system.infrastructure;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 短信签名（JDBC）：按服务商 + 国家 + 语言维护。 */
@Repository
public class SmsSignatureRepository {

    private final JdbcTemplate jdbc;

    public SmsSignatureRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<SignatureRow> findAll(String providerCode) {
        if (providerCode == null || providerCode.isBlank()) {
            return jdbc.query("""
                    select id, provider_code, sign_name, country_code, locale, status, provider_approval_status
                      from sys_sms_signature where deleted_at is null
                     order by provider_code, country_code, locale
                    """, (rs, rowNum) -> row(rs));
        }
        return jdbc.query("""
                select id, provider_code, sign_name, country_code, locale, status, provider_approval_status
                  from sys_sms_signature where deleted_at is null and provider_code = ?
                 order by country_code, locale
                """, (rs, rowNum) -> row(rs), providerCode);
    }

    public Optional<SignatureRow> findById(UUID id) {
        return jdbc.query("""
                select id, provider_code, sign_name, country_code, locale, status, provider_approval_status
                  from sys_sms_signature where id = ? and deleted_at is null
                """, (rs, rowNum) -> row(rs), id).stream().findFirst();
    }

    public Optional<String> findSignName(UUID id) {
        return findById(id).map(SignatureRow::signName);
    }

    public void insert(SignatureRow row) {
        jdbc.update("""
                insert into sys_sms_signature (provider_code, sign_name, country_code, locale, status,
                                               provider_approval_status)
                values (?, ?, ?, ?, ?, ?)
                """, row.providerCode(), row.signName(), row.countryCode(), row.locale(),
                row.status(), row.approvalStatus());
    }

    public void update(SignatureRow row) {
        jdbc.update("""
                update sys_sms_signature
                   set sign_name = ?, country_code = ?, locale = ?, status = ?,
                       provider_approval_status = ?, updated_at = now()
                 where id = ? and deleted_at is null
                """, row.signName(), row.countryCode(), row.locale(), row.status(),
                row.approvalStatus(), row.id());
    }

    private static SignatureRow row(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new SignatureRow(rs.getObject("id", UUID.class), rs.getString("provider_code"),
                rs.getString("sign_name"), rs.getString("country_code"), rs.getString("locale"),
                rs.getString("status"), rs.getString("provider_approval_status"));
    }

    public record SignatureRow(UUID id, String providerCode, String signName, String countryCode,
                               String locale, String status, String approvalStatus) {
    }
}
