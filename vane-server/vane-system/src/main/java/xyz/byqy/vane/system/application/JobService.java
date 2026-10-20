package xyz.byqy.vane.system.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import xyz.byqy.vane.common.error.ApiException;
import xyz.byqy.vane.common.error.CommonErrors;
import xyz.byqy.vane.system.domain.SysJob;
import xyz.byqy.vane.system.domain.SysJobRepository;

/** 定时任务管理（ADM-11）：cron 变更后重新注册调度。 */
@Service
public class JobService {

    private final SysJobRepository jobs;
    private final JobScheduler scheduler;

    public JobService(SysJobRepository jobs, JobScheduler scheduler) {
        this.jobs = jobs;
        this.scheduler = scheduler;
    }

    @Transactional(readOnly = true)
    public List<JobView> list() {
        return jobs.findByDeletedAtIsNullOrderByJobCodeAsc().stream().map(this::toView).toList();
    }

    @Transactional
    public JobView create(String jobCode, String nameKey, String handler, String cron, String params,
                          String concurrency) {
        if (jobs.existsByJobCodeAndDeletedAtIsNull(jobCode)) {
            throw new ApiException(CommonErrors.REQUEST_PRECONDITION_FAILED);
        }
        validateCron(cron);
        SysJob job = SysJob.create(UUID.randomUUID(), jobCode, nameKey, handler, cron, params,
                concurrency == null ? "FORBID" : concurrency);
        jobs.save(job);
        scheduler.sync(job);
        return toView(job);
    }

    @Transactional
    public JobView update(UUID id, String nameKey, String cron, String params, String status, String concurrency) {
        SysJob job = require(id);
        validateCron(cron);
        job.update(nameKey, cron, params, status, concurrency == null ? job.getConcurrency() : concurrency);
        jobs.save(job);
        scheduler.sync(job);
        return toView(job);
    }

    @Transactional
    public void delete(UUID id) {
        SysJob job = require(id);
        job.softDelete();
        jobs.save(job);
        scheduler.cancel(id);
    }

    @Transactional(readOnly = true)
    public void runNow(UUID id) {
        require(id);
    }

    public void trigger(UUID id) {
        require(id);
        scheduler.runNow(id);
    }

    private void validateCron(String cron) {
        if (cron == null || !CronExpression.isValidExpression(cron)) {
            throw new ApiException(CommonErrors.VALIDATION_FAILED);
        }
    }

    private SysJob require(UUID id) {
        return jobs.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ApiException(CommonErrors.REQUEST_NOT_FOUND));
    }

    private JobView toView(SysJob job) {
        return new JobView(job.getId(), job.getJobCode(), job.getNameKey(), job.getHandler(), job.getCron(),
                job.getParams(), job.getStatus(), job.getConcurrency(), job.getLastRunAt(), job.getNextRunAt());
    }

    public record JobView(UUID id, String jobCode, String nameKey, String handler, String cron, String params,
                          String status, String concurrency, Instant lastRunAt, Instant nextRunAt) {
    }
}
