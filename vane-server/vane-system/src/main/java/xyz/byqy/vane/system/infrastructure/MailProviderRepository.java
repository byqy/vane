package xyz.byqy.vane.system.infrastructure;

import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 邮件服务商配置（JDBC）：password_cipher 为密文，加解密在应用层完成（ADR-024）。 */
@Repository
public class MailProviderRepository {

    private final JdbcTemplate jdbc;

    public MailProviderRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<MailProviderRow> findAll() {
        return jdbc.query("""
                select code, name, host, port, tls_mode, username, password_cipher, key_version,
                       from_address::text as from_address, from_name, enabled, is_default,
                       daily_limit, timeout_ms
                  from sys_mail_provider
                 order by code
                """, (rs, rowNum) -> map(rs));
    }

    public Optional<MailProviderRow> findByCode(String code) {
        return jdbc.query("""
                select code, name, host, port, tls_mode, username, password_cipher, key_version,
                       from_address::text as from_address, from_name, enabled, is_default,
                       daily_limit, timeout_ms
                  from sys_mail_provider
                 where code = ?
                """, (rs, rowNum) -> map(rs), code).stream().findFirst();
    }

    public Optional<MailProviderRow> findDefault() {
        return jdbc.query("""
                select code, name, host, port, tls_mode, username, password_cipher, key_version,
                       from_address::text as from_address, from_name, enabled, is_default,
                       daily_limit, timeout_ms
                  from sys_mail_provider
                 where is_default and enabled
                """, (rs, rowNum) -> map(rs)).stream().findFirst();
    }

    public long count() {
        Long count = jdbc.queryForObject("select count(*) from sys_mail_provider", Long.class);
        return count == null ? 0 : count;
    }

    public void insert(MailProviderRow row) {
        jdbc.update("""
                insert into sys_mail_provider (code, name, host, port, tls_mode, username, password_cipher,
                                               key_version, from_address, from_name, enabled, is_default,
                                               daily_limit, timeout_ms)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, row.code(), row.name(), row.host(), row.port(), row.tlsMode(), row.username(),
                row.passwordCipher(), row.keyVersion(), row.fromAddress(), row.fromName(), row.enabled(),
                row.isDefault(), row.dailyLimit(), row.timeoutMs());
    }

    public void update(MailProviderRow row) {
        jdbc.update("""
                update sys_mail_provider
                   set name = ?, host = ?, port = ?, tls_mode = ?, username = ?, password_cipher = ?,
                       key_version = ?, from_address = ?, from_name = ?, enabled = ?, is_default = ?,
                       daily_limit = ?, timeout_ms = ?, updated_at = now()
                 where code = ?
                """, row.name(), row.host(), row.port(), row.tlsMode(), row.username(), row.passwordCipher(),
                row.keyVersion(), row.fromAddress(), row.fromName(), row.enabled(), row.isDefault(),
                row.dailyLimit(), row.timeoutMs(), row.code());
    }

    public void delete(String code) {
        jdbc.update("delete from sys_mail_provider where code = ?", code);
    }

    /** 取消其它行的默认标记（切换默认时在同一事务内调用）。 */
    public void clearDefaultExcept(String code) {
        jdbc.update("update sys_mail_provider set is_default = false, updated_at = now() "
                + "where is_default and code <> ?", code);
    }

    private static MailProviderRow map(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new MailProviderRow(rs.getString("code"), rs.getString("name"), rs.getString("host"),
                rs.getInt("port"), rs.getString("tls_mode"), rs.getString("username"),
                rs.getString("password_cipher"), rs.getString("key_version"), rs.getString("from_address"),
                rs.getString("from_name"), rs.getBoolean("enabled"), rs.getBoolean("is_default"),
                rs.getInt("daily_limit"), rs.getInt("timeout_ms"));
    }

    /** 服务商行（密码为密文，null 表示未配置）。 */
    public record MailProviderRow(String code, String name, String host, int port, String tlsMode,
                                  String username, String passwordCipher, String keyVersion,
                                  String fromAddress, String fromName, boolean enabled, boolean isDefault,
                                  int dailyLimit, int timeoutMs) {
    }
}
