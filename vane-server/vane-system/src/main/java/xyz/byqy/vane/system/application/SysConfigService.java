package xyz.byqy.vane.system.application;

import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import xyz.byqy.vane.common.error.ApiException;
import xyz.byqy.vane.common.error.CommonErrors;

/** 系统参数（ADM-06）：内置只读参数 editable = false，修改时拒绝。 */
@Service
public class SysConfigService {

    private final JdbcTemplate jdbc;

    public SysConfigService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<ConfigView> list() {
        return jdbc.query("""
                select config_key, value_json::text as value_json, value_type, description, editable
                from sys_config order by config_key
                """, (rs, rowNum) -> new ConfigView(rs.getString("config_key"), rs.getString("value_json"),
                rs.getString("value_type"), rs.getString("description"), rs.getBoolean("editable")));
    }

    @Transactional(readOnly = true)
    public String getString(String key, String fallback) {
        List<String> values = jdbc.query("""
                select value_json #>> '{}' as value_text from sys_config where config_key = ?
                """, (rs, rowNum) -> rs.getString("value_text"), key);
        return values.isEmpty() || values.get(0) == null ? fallback : values.get(0);
    }

    /** 读取整型参数，缺省时返回 fallback。 */
    @Transactional

    public int getInt(String key, int fallback) {
        List<Integer> values = jdbc.query("""
                select value_json #>> '{}' as value_text from sys_config where config_key = ?
                """, (rs, rowNum) -> Integer.valueOf(rs.getString("value_text").trim()), key);
        return values.isEmpty() ? fallback : values.get(0);
    }

    public ConfigView update(String key, String valueJson, UUID actorId) {
        List<ConfigView> found = jdbc.query("""
                select config_key, value_json::text as value_json, value_type, description, editable
                from sys_config where config_key = ?
                """, (rs, rowNum) -> new ConfigView(rs.getString("config_key"), rs.getString("value_json"),
                rs.getString("value_type"), rs.getString("description"), rs.getBoolean("editable")), key);
        if (found.isEmpty()) {
            throw new ApiException(CommonErrors.SYS_CONFIG_NOT_FOUND);
        }
        ConfigView current = found.get(0);
        if (!current.editable()) {
            throw new ApiException(CommonErrors.SYS_CONFIG_READONLY);
        }
        jdbc.update("update sys_config set value_json = cast(? as jsonb), updated_at = now(), updated_by = ? where config_key = ?",
                valueJson, actorId, key);
        return new ConfigView(key, valueJson, current.valueType(), current.description(), true);
    }
}






