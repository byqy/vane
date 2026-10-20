package xyz.byqy.vane.system.application;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** 内置任务：清理过期的幂等键。 */
@Component
public class CleanupIdempotencyHandler implements JobHandler {

    private final JdbcTemplate jdbc;

    public CleanupIdempotencyHandler(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public String name() {
        return "cleanup-idempotency";
    }

    @Override
    public void run(String params) {
        jdbc.update("delete from sys_idempotency_key where expires_at < now()");
    }
}
