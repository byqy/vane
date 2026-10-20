package xyz.byqy.vane.system.application;

import java.time.Instant;
import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 任务执行记录写入（独立事务，失败不影响调度）。 */
@Component
public class JobLogWriter {

    private final JdbcTemplate jdbc;

    public JobLogWriter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void write(UUID jobId, Instant startedAt, Instant finishedAt, boolean success, String message) {
        jdbc.update("""
                insert into sys_job_log (id, job_id, started_at, finished_at, success, message, trace_id)
                values (?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), jobId, java.sql.Timestamp.from(startedAt),
                java.sql.Timestamp.from(finishedAt), success, message, MDC.get("traceId"));
    }
}
