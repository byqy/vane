package xyz.byqy.vane.api.admin;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import xyz.byqy.vane.audit.api.Audit;
import xyz.byqy.vane.system.application.JobService;
import xyz.byqy.vane.system.application.SysJobLogQueryService;

/** 定时任务管理（ADM-11）。 */
@RestController
@RequestMapping("/api/v1/admin/jobs")
public class AdminJobController {

    public record CreateJobRequest(@NotBlank String jobCode, @NotBlank String nameKey, @NotBlank String handler,
                                   @NotBlank String cron, String params, String concurrency) {
    }

    public record UpdateJobRequest(@NotBlank String nameKey, @NotBlank String cron, String params,
                                   @Pattern(regexp = "ACTIVE|PAUSED") String status, String concurrency) {
    }

    private final JobService jobs;
    private final SysJobLogQueryService jobLogs;

    public AdminJobController(JobService jobs, SysJobLogQueryService jobLogs) {
        this.jobs = jobs;
        this.jobLogs = jobLogs;
    }

    @GetMapping("/{id}/logs")
    @PreAuthorize("hasAuthority(T(xyz.byqy.vane.iam.domain.PermissionCatalog).OPS_JOB.code)")
    public SysJobLogQueryService.PageResult<SysJobLogQueryService.JobLogView> logs(
            @PathVariable UUID id,
            @RequestParam(name = "page[number]", defaultValue = "0") int pageNumber,
            @RequestParam(name = "page[size]", defaultValue = "20") int pageSize) {
        return jobLogs.page(id, pageNumber, pageSize);
    }

    @GetMapping
    @PreAuthorize("hasAuthority(T(xyz.byqy.vane.iam.domain.PermissionCatalog).OPS_JOB.code)")
    public List<JobService.JobView> list() {
        return jobs.list();
    }

    @PostMapping
    @PreAuthorize("hasAuthority(T(xyz.byqy.vane.iam.domain.PermissionCatalog).OPS_JOB_CREATE.code)")
    @Audit(module = "job", action = "create")
    public ResponseEntity<JobService.JobView> create(@Valid @RequestBody CreateJobRequest request) {
        JobService.JobView created = jobs.create(request.jobCode(), request.nameKey(), request.handler(),
                request.cron(), request.params(), request.concurrency());
        return ResponseEntity.created(URI.create("/api/v1/admin/jobs/" + created.id())).body(created);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority(T(xyz.byqy.vane.iam.domain.PermissionCatalog).OPS_JOB_UPDATE.code)")
    @Audit(module = "job", action = "update")
    public JobService.JobView update(@PathVariable UUID id, @Valid @RequestBody UpdateJobRequest request) {
        return jobs.update(id, request.nameKey(), request.cron(), request.params(), request.status(),
                request.concurrency());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority(T(xyz.byqy.vane.iam.domain.PermissionCatalog).OPS_JOB_DELETE.code)")
    @Audit(module = "job", action = "delete")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        jobs.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/run")
    @PreAuthorize("hasAuthority(T(xyz.byqy.vane.iam.domain.PermissionCatalog).OPS_JOB_RUN.code)")
    @Audit(module = "job", action = "run")
    public ResponseEntity<Void> run(@PathVariable UUID id) {
        jobs.trigger(id);
        return ResponseEntity.accepted().build();
    }
}




