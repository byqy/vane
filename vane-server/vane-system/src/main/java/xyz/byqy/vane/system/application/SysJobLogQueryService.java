package xyz.byqy.vane.system.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import xyz.byqy.vane.common.error.ApiException;
import xyz.byqy.vane.common.error.CommonErrors;

/** 定时任务执行日志（ADM-11）：按任务查询，倒序分页。 */
@Service
public class SysJobLogQueryService {

    private final JdbcTemplate jdbc;

    public SysJobLogQueryService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public PageResult<JobLogView> page(UUID jobId, int pageNumber, int pageSize) {
        Integer exists = jdbc.queryForObject("select count(*) from sys_job where id = ? and deleted_at is null",
                Integer.class, jobId);
        if (exists == null || exists == 0) {
            throw new ApiException(CommonErrors.REQUEST_NOT_FOUND);
        }
        int size = Math.min(Math.max(pageSize, 1), 100);
        int number = Math.max(pageNumber, 0);

        Long total = jdbc.queryForObject("select count(*) from sys_job_log where job_id = ?", Long.class, jobId);
        List<JobLogView> items = jdbc.query("""
                select id, started_at, finished_at, success, message, trace_id
                from sys_job_log where job_id = ?
                order by started_at desc
                limit ? offset ?
                """, (rs, rowNum) -> new JobLogView(
                rs.getObject("id", UUID.class),
                rs.getTimestamp("started_at") == null ? null : rs.getTimestamp("started_at").toInstant(),
                rs.getTimestamp("finished_at") == null ? null : rs.getTimestamp("finished_at").toInstant(),
                rs.getObject("success") == null ? null : rs.getBoolean("success"),
                rs.getString("message"),
                rs.getString("trace_id")), jobId, size, number * size);

        return new PageResult<>(items, new PageResult.PageMeta(number, size, total == null ? 0 : total));
    }

    public record JobLogView(UUID id, Instant startedAt, Instant finishedAt, Boolean success, String message,
                             String traceId) {
    }

    public record PageResult<T>(List<T> items, PageMeta page) {
        public record PageMeta(int number, int size, long total) {
        }
    }
}
