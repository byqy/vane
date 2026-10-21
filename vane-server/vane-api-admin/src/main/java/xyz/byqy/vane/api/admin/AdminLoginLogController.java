package xyz.byqy.vane.api.admin;

import java.time.Instant;

import org.springframework.security.access.prepost.PreAuthorize;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import xyz.byqy.vane.api.support.CsvExporter;
import xyz.byqy.vane.audit.api.Audit;
import xyz.byqy.vane.common.audit.AuditContext;
import xyz.byqy.vane.iam.application.LoginLogQueryService;
import xyz.byqy.vane.iam.application.PageResult;

/** 登录日志查询（ADM-10）。 */
@RestController
@RequestMapping("/api/v1/admin/login-logs")
public class AdminLoginLogController {

    private final LoginLogQueryService logs;

    public AdminLoginLogController(LoginLogQueryService logs) {
        this.logs = logs;
    }

    @GetMapping
    @PreAuthorize("hasAuthority(T(xyz.byqy.vane.iam.domain.PermissionCatalog).AUDIT_LOGIN_LOG.code)")
    public PageResult<LoginLogQueryService.LoginLogView> list(
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String result,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(name = "page[number]", defaultValue = "0") int pageNumber,
            @RequestParam(name = "page[size]", defaultValue = "20") int pageSize) {
        return logs.page(username, result, from, to, pageNumber, pageSize);
    }

    @GetMapping("/export")
    @Audit(module = "audit", action = "export", targetType = "login-log")
    @PreAuthorize("hasAuthority(T(xyz.byqy.vane.iam.domain.PermissionCatalog).AUDIT_LOGIN_LOG_EXPORT.code)")
    public void export(
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String result,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            HttpServletResponse response) throws java.io.IOException {
        var rows = logs.exportRows(username, result, from, to).stream()
                .map(log -> java.util.List.of(
                        String.valueOf(log.createdAt()),
                        log.username(),
                        log.result(),
                        log.detail() == null ? "" : log.detail(),
                        log.ip() == null ? "" : log.ip(),
                        log.traceId() == null ? "" : log.traceId()))
                .toList();
        AuditContext.setAffectedCount(rows.size());
        CsvExporter.write(response, "login-logs.csv",
                java.util.List.of("时间", "用户", "结果", "说明", "IP", "traceId"), rows);
    }
}

