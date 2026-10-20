package xyz.byqy.vane.system.application;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import xyz.byqy.vane.system.domain.SysJob;
import xyz.byqy.vane.system.domain.SysJobRepository;

/**
 * 应用内调度器（ADM-11）：不引入 Quartz。
 * 采用「定时 tick + CronExpression 计算下次执行」的方式，避免依赖 Trigger 调度 API。
 * 只执行 status=ACTIVE 且 handler 已注册的任务。
 */
@Component
public class JobScheduler {

    private static final Logger log = LoggerFactory.getLogger(JobScheduler.class);
    private static final Duration TICK = Duration.ofSeconds(10);
    private static final ZoneId ZONE = ZoneId.systemDefault();

    private final SysJobRepository jobs;
    private final JobLogWriter logWriter;
    private final Map<String, JobHandler> handlers;
    private final Map<UUID, AtomicBoolean> running = new ConcurrentHashMap<>();
    private final ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();

    public JobScheduler(SysJobRepository jobs, JobLogWriter logWriter, @Lazy java.util.List<JobHandler> handlerList) {
        this.jobs = jobs;
        this.logWriter = logWriter;
        this.handlers = handlerList.stream().collect(Collectors.toMap(JobHandler::name, Function.identity()));
        this.scheduler.setPoolSize(2);
        this.scheduler.setThreadNamePrefix("vane-job-");
    }

    @PostConstruct
    public void start() {
        scheduler.initialize();
        jobs.findByStatusAndDeletedAtIsNull("ACTIVE").forEach(job -> {
            if (handlers.containsKey(job.getHandler())) {
                sync(job);
            } else {
                log.warn("Job {} references unknown handler {}", job.getJobCode(), job.getHandler());
            }
        });
        scheduler.scheduleWithFixedDelay(this::tick, TICK);
    }

    @PreDestroy
    public void stop() {
        scheduler.shutdown();
    }

    /** 新建或修改后重新计算下次执行时间。 */
    public void sync(SysJob job) {
        if (!"ACTIVE".equals(job.getStatus())) {
            return;
        }
        try {
            Instant next = CronExpression.parse(job.getCron()).next(LocalDateTime.now()).atZone(ZONE).toInstant();
            job.markNext(next);
            jobs.save(job);
        } catch (IllegalArgumentException ex) {
            log.warn("Invalid cron for job {}: {}", job.getJobCode(), job.getCron());
        }
    }

    public void cancel(UUID jobId) {
        running.remove(jobId);
    }

    /** 手动触发（与调度共用执行路径）。 */
    public void runNow(UUID jobId) {
        execute(jobId);
    }

    private void tick() {
        try {
            Instant now = Instant.now();
            for (SysJob job : jobs.findByStatusAndDeletedAtIsNull("ACTIVE")) {
                if (job.getNextRunAt() == null || !job.getNextRunAt().isAfter(now)) {
                    execute(job.getId());
                }
            }
        } catch (RuntimeException ex) {
            log.warn("Job tick failed", ex);
        }
    }

    private void execute(UUID jobId) {
        SysJob job = jobs.findByIdAndDeletedAtIsNull(jobId).orElse(null);
        if (job == null) {
            return;
        }
        AtomicBoolean flag = running.computeIfAbsent(jobId, key -> new AtomicBoolean(false));
        if ("FORBID".equals(job.getConcurrency()) && !flag.compareAndSet(false, true)) {
            log.info("Skip job {} because previous run is still in progress", job.getJobCode());
            return;
        }
        if ("ALLOW".equals(job.getConcurrency())) {
            flag.set(true);
        }
        Instant startedAt = Instant.now();
        boolean success = true;
        String message = null;
        try {
            JobHandler handler = handlers.get(job.getHandler());
            if (handler == null) {
                throw new IllegalStateException("Unknown handler: " + job.getHandler());
            }
            handler.run(job.getParams());
        } catch (Exception ex) {
            success = false;
            message = ex.getClass().getSimpleName() + ": " + ex.getMessage();
            log.warn("Job {} failed", job.getJobCode(), ex);
        } finally {
            flag.set(false);
            Instant finishedAt = Instant.now();
            job.markRun(finishedAt);
            try {
                job.markNext(CronExpression.parse(job.getCron()).next(LocalDateTime.now()).atZone(ZONE).toInstant());
            } catch (RuntimeException ignored) {
                // cron 已校验
            }
            jobs.save(job);
            logWriter.write(job.getId(), startedAt, finishedAt, success, message);
        }
    }
}

