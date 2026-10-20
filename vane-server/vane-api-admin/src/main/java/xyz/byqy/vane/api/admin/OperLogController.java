package xyz.byqy.vane.api.admin;

import java.time.Instant;

import org.springframework.security.access.prepost.PreAuthorize;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import xyz.byqy.vane.api.support.CsvExporter;
import xyz.byqy.vane.audit.application.OperLogQueryService;
import xyz.byqy.vane.audit.application.OperLogView;
import xyz.byqy.vane.audit.application.PageResult;

/** 操作日志查询（ADM-10），只读；权限点 audit:oper-log。 */
@RestController
@RequestMapping("/api/v1/admin/oper-logs")
public class OperLogController {

    private final OperLogQueryService logs;

    public OperLogController(OperLogQueryService logs) {
        this.logs = logs;
    }

    @GetMapping
    @PreAuthorize("hasAuthority(T(xyz.byqy.vane.iam.domain.PermissionCatalog).AUDIT_OPER_LOG.code)")
    public PageResult<OperLogView> list(
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String result,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(name = "page[number]", defaultValue = "0") int pageNumber,
            @RequestParam(name = "page[size]", defaultValue = "20") int pageSize) {
        return logs.page(module, action, result, username, from, to, pageNumber, pageSize);
    }

    @GetMapping("/export")
    @PreAuthorize("hasAuthority(T(xyz.byqy.vane.iam.domain.PermissionCatalog).AUDIT_OPER_LOG_EXPORT.code)")
    public void export(
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String result,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            HttpServletResponse response) throws java.io.IOException {
        var rows = logs.exportRows(module, action, result, username, from, to).stream()
                .map(log -> java.util.List.of(
                        String.valueOf(log.createdAt()),
                        nullToEmpty(log.username()),
                        log.module(),
                        log.action(),
                        nullToEmpty(log.httpMethod()),
                        nullToEmpty(log.path()),
                        nullToEmpty(log.params()),
                        log.result(),
                        nullToEmpty(log.errorCode()),
                        log.durationMs() == null ? "" : String.valueOf(log.durationMs()),
                        nullToEmpty(log.ip()),
                        nullToEmpty(log.traceId())))
                .toList();
        CsvExporter.write(response, "oper-logs.csv",
                java.util.List.of("时间", "用户", "模块", "操作", "方法", "路径", "参数", "结果", "错误码", "耗时(ms)", "IP", "traceId"),
                rows);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}

