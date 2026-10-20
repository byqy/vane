package xyz.byqy.vane.system.infrastructure;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 模板 ↔ 厂商模板码/签名/变量映射（JDBC）：按 provider + 国家 + 语言绑定。 */
@Repository
public class SmsBindingRepository {

    private final JdbcTemplate jdbc;

    public SmsBindingRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<BindingRow> findByTemplate(UUID templateId) {
        return jdbc.query("""
                select b.id, b.template_id, t.code as template_code, b.provider_code, b.provider_template_code,
                       b.signature_id, b.country_code, b.locale, b.param_mapping::text as param_mapping
                  from sys_sms_template_binding b
                  join sys_message_template t on t.id = b.template_id
                 where b.deleted_at is null and b.template_id = ?
                 order by b.provider_code, b.country_code, b.locale
                """, (rs, rowNum) -> row(rs), templateId);
    }

    public List<BindingRow> findAll(String providerCode) {
        String sql = """
                select b.id, b.template_id, t.code as template_code, b.provider_code, b.provider_template_code,
                       b.signature_id, b.country_code, b.locale, b.param_mapping::text as param_mapping
                  from sys_sms_template_binding b
                  join sys_message_template t on t.id = b.template_id
                 where b.deleted_at is null
                """;
        if (providerCode == null || providerCode.isBlank()) {
            return jdbc.query(sql + " order by t.code, b.provider_code, b.country_code, b.locale",
                    (rs, rowNum) -> row(rs));
        }
        return jdbc.query(sql + " and b.provider_code = ? order by t.code, b.country_code, b.locale",
                (rs, rowNum) -> row(rs), providerCode);
    }

    public Optional<BindingRow> find(UUID templateId, String providerCode, String countryCode, String locale) {
        return jdbc.query("""
                select b.id, b.template_id, t.code as template_code, b.provider_code, b.provider_template_code,
                       b.signature_id, b.country_code, b.locale, b.param_mapping::text as param_mapping
                  from sys_sms_template_binding b
                  join sys_message_template t on t.id = b.template_id
                 where b.deleted_at is null and b.template_id = ? and b.provider_code = ?
                   and b.country_code = ? and b.locale = ?
                """, (rs, rowNum) -> row(rs), templateId, providerCode, countryCode, locale)
                .stream().findFirst();
    }

    /** 整体替换某个模板的全部绑定。 */
    public void replaceForTemplate(UUID templateId, List<BindingRow> rows) {
        jdbc.update("delete from sys_sms_template_binding where template_id = ?", templateId);
        for (BindingRow row : rows) {
            jdbc.update("""
                    insert into sys_sms_template_binding (template_id, provider_code, provider_template_code,
                                                          signature_id, country_code, locale, param_mapping)
                    values (?, ?, ?, ?, ?, ?, ?::jsonb)
                    """, templateId, row.providerCode(), row.providerTemplateCode(), row.signatureId(),
                    row.countryCode(), row.locale(), row.paramMappingJson() == null ? "{}" : row.paramMappingJson());
        }
    }

    private static BindingRow row(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new BindingRow(rs.getObject("id", UUID.class), rs.getObject("template_id", UUID.class),
                rs.getString("template_code"), rs.getString("provider_code"), rs.getString("provider_template_code"),
                rs.getObject("signature_id", UUID.class), rs.getString("country_code"), rs.getString("locale"),
                rs.getString("param_mapping"));
    }

    public record BindingRow(UUID id, UUID templateId, String templateCode, String providerCode,
                             String providerTemplateCode, UUID signatureId, String countryCode,
                             String locale, String paramMappingJson) {
    }
}
