package xyz.byqy.vane.system.domain;

import xyz.byqy.vane.common.persistence.AuditableEntity;
import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** sys_job（05 §5.7）：cron 表达式 + handler 名称，由应用内调度器执行。 */
@Entity
@Table(name = "sys_job")
public class SysJob extends AuditableEntity {

    @Id
    private UUID id;

    @Column(name = "job_code", nullable = false)
    private String jobCode;

    @Column(name = "name_key", nullable = false)
    private String nameKey;

    @Column(nullable = false)
    private String handler;

    @Column(nullable = false)
    private String cron;

    @JdbcTypeCode(SqlTypes.JSON)
    private String params;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private String concurrency;

    @Column(name = "last_run_at")
    private Instant lastRunAt;

    @Column(name = "next_run_at")
    private Instant nextRunAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected SysJob() {
    }

    public static SysJob create(UUID id, String jobCode, String nameKey, String handler, String cron,
                                String params, String concurrency) {
        SysJob job = new SysJob();
        job.id = id;
        job.jobCode = jobCode;
        job.nameKey = nameKey;
        job.handler = handler;
        job.cron = cron;
        job.params = params;
        job.concurrency = concurrency;
        job.status = "ACTIVE";
        job.createdAt = Instant.now();
        return job;
    }

    public void update(String nameKey, String cron, String params, String status, String concurrency) {
        this.nameKey = nameKey;
        this.cron = cron;
        this.params = params;
        this.concurrency = concurrency;
        if (status != null) {
            this.status = status;
        }
    }

    public void markRun(Instant at) {
        this.lastRunAt = at;
    }

    public void markNext(Instant at) {
        this.nextRunAt = at;
    }

    public void softDelete() {
        this.deletedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getJobCode() {
        return jobCode;
    }

    public String getNameKey() {
        return nameKey;
    }

    public String getHandler() {
        return handler;
    }

    public String getCron() {
        return cron;
    }

    public String getParams() {
        return params;
    }

    public String getStatus() {
        return status;
    }

    public String getConcurrency() {
        return concurrency;
    }

    public Instant getLastRunAt() {
        return lastRunAt;
    }

    public Instant getNextRunAt() {
        return nextRunAt;
    }
}

