package xyz.byqy.vane.system.application;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** 聚合健康检查（ADM-12）：只暴露「依赖是否可用」，不泄露 actuator 细节。 */
@Service
public class SystemHealthService {

    private final JdbcTemplate jdbc;
    private final StringRedisTemplate redis;

    public SystemHealthService(JdbcTemplate jdbc, StringRedisTemplate redis) {
        this.jdbc = jdbc;
        this.redis = redis;
    }

    public HealthView check() {
        Map<String, String> checks = new LinkedHashMap<>();
        checks.put("database", probe(() -> jdbc.queryForObject("select 1", Integer.class)));
        checks.put("cache", probe(() -> redis.getConnectionFactory().getConnection().ping()));
        boolean up = checks.values().stream().allMatch("UP"::equals);
        return new HealthView(up ? "UP" : "DEGRADED", checks, Instant.now());
    }

    private String probe(Runnable action) {
        try {
            action.run();
            return "UP";
        } catch (RuntimeException ex) {
            return "DOWN";
        }
    }

    public record HealthView(String status, Map<String, String> checks, Instant checkedAt) {
    }
}
