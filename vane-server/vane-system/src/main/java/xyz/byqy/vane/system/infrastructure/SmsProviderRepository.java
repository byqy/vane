package xyz.byqy.vane.system.infrastructure;

import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 短信服务商配置（JDBC）：config_cipher 为密文，加解密在应用层完成。 */
@Repository
public class SmsProviderRepository {

    private final JdbcTemplate jdbc;

    public SmsProviderRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<SmsProviderRow> findAll() {
        return jdbc.query("""
                select provider_code, provider_type, name, config_cipher, key_version,
                       countries::text as countries, optout_keywords::text as optout_keywords,
                       enabled, is_default
                  from sys_sms_provider
                 order by provider_code
                """, (rs, rowNum) -> new SmsProviderRow(
                rs.getString("provider_code"), rs.getString("provider_type"), rs.getString("name"),
                rs.getString("config_cipher"), rs.getString("key_version"), rs.getString("countries"),
                rs.getString("optout_keywords"), rs.getBoolean("enabled"), rs.getBoolean("is_default")));
    }

    public Optional<SmsProviderRow> findByCode(String code) {
        return jdbc.query("""
                select provider_code, provider_type, name, config_cipher, key_version,
                       countries::text as countries, optout_keywords::text as optout_keywords,
                       enabled, is_default
                  from sys_sms_provider
                 where provider_code = ?
                """, (rs, rowNum) -> new SmsProviderRow(
                rs.getString("provider_code"), rs.getString("provider_type"), rs.getString("name"),
                rs.getString("config_cipher"), rs.getString("key_version"), rs.getString("countries"),
                rs.getString("optout_keywords"), rs.getBoolean("enabled"), rs.getBoolean("is_default")), code)
                .stream().findFirst();
    }

    public Optional<SmsProviderRow> findDefault() {
        return jdbc.query("""
                select provider_code, provider_type, name, config_cipher, key_version,
                       countries::text as countries, optout_keywords::text as optout_keywords,
                       enabled, is_default
                  from sys_sms_provider
                 where is_default and enabled
                """, (rs, rowNum) -> new SmsProviderRow(
                rs.getString("provider_code"), rs.getString("provider_type"), rs.getString("name"),
                rs.getString("config_cipher"), rs.getString("key_version"), rs.getString("countries"),
                rs.getString("optout_keywords"), rs.getBoolean("enabled"), rs.getBoolean("is_default")))
                .stream().findFirst();
    }

    public void insert(SmsProviderRow row) {
        jdbc.update("""
                insert into sys_sms_provider (provider_code, provider_type, name, config_cipher, key_version,
                                              countries, optout_keywords, enabled, is_default)
                values (?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?)
                """, row.code(), row.type(), row.name(), row.configCipher(), row.keyVersion(),
                row.countriesJson(), row.optoutKeywordsJson(), row.enabled(), row.isDefault());
    }

    public void update(SmsProviderRow row) {
        jdbc.update("""
                update sys_sms_provider
                   set name = ?, config_cipher = ?, key_version = ?, countries = ?::jsonb,
                       optout_keywords = ?::jsonb, enabled = ?, is_default = ?, updated_at = now()
                 where provider_code = ?
                """, row.name(), row.configCipher(), row.keyVersion(), row.countriesJson(),
                row.optoutKeywordsJson(), row.enabled(), row.isDefault(), row.code());
    }

    /** 设置默认前清掉其它默认项（唯一索引兜底）。 */
    public void clearDefaultExcept(String code) {
        jdbc.update("""
                update sys_sms_provider set is_default = false, updated_at = now()
                 where provider_code <> ? and is_default
                """, code);
    }

    /** 服务商配置行（密文与 JSON 原文）。 */
    public record SmsProviderRow(String code, String type, String name, String configCipher, String keyVersion,
                                 String countriesJson, String optoutKeywordsJson, boolean enabled, boolean isDefault) {
    }
}
