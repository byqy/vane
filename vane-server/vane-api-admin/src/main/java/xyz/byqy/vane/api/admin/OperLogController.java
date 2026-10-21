package xyz.byqy.vane.api.admin;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletResponse;
import xyz.byqy.vane.api.support.CsvExporter;
import xyz.byqy.vane.audit.api.Audit;
import xyz.byqy.vane.audit.application.OperLogDetail;
import xyz.byqy.vane.audit.application.OperLogQueryService;
import xyz.byqy.vane.audit.application.OperLogSummary;
import xyz.byqy.vane.audit.application.PageResult;
import xyz.byqy.vane.common.audit.AuditContext;

/** 操作日志查询（ADM-10），只读；权限点 audit:oper-log / :detail / :export。 */
@RestController
@RequestMapping("/api/v1/admin/oper-logs")
public class OperLogController {

    private final OperLogQueryService logs;

    public OperLogController(OperLogQueryService logs) {
        this.logs = logs;
    }

    @GetMapping
    @PreAuthorize("hasAuthority(T(xyz.byqy.vane.iam.domain.PermissionCatalog).AUDIT_OPER_LOG.code)")
    public PageResult<OperLogSummary> list(
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String result,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) String targetId,
            @RequestParam(required = false) String traceId,
            @RequestParam(required = false) String ip,
            @RequestParam(required = false) Integer httpStatus,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(name = "page[number]", defaultValue = "0") int pageNumber,
            @RequestParam(name = "page[size]", defaultValue = "20") int pageSize) {
        return logs.page(module, action, result, username, source, targetType, targetId, traceId, ip,
                httpStatus, from, to, pageNumber, pageSize);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority(T(xyz.byqy.vane.iam.domain.PermissionCatalog).AUDIT_OPER_LOG_DETAIL.code)")
    public OperLogDetail detail(@PathVariable UUID id) {
        return logs.detail(id);
    }

    @GetMapping("/export")
    @PreAuthorize("hasAuthority(T(xyz.byqy.vane.iam.domain.PermissionCatalog).AUDIT_OPER_LOG_EXPORT.code)")
    @Audit(module = "audit", action = "export", targetType = "oper-log")
    public void export(
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String result,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) String targetId,
            @RequestParam(required = false) String traceId,
            @RequestParam(required = false) String ip,
            @RequestParam(required = false) Integer httpStatus,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            HttpServletResponse response) throws java.io.IOException {
        var rows = logs.exportRows(module, action, result, username, source, targetType, targetId, traceId, ip,
                httpStatus, from, to);
        AuditContext.setAffectedCount(rows.size());
        List<List<String>> cells = new ArrayList<>();
        for (OperLogDetail log : rows) {
            cells.add(List.of(
                    String.valueOf(log.createdAt()),
                    nullToEmpty(log.username()),
                    log.module(),
                    log.action(),
                    nullToEmpty(log.source()),
                    nullToEmpty(log.targetType()),
                    nullToEmpty(log.targetId()),
                    nullToEmpty(log.targetLabel()),
                    nullToEmpty(log.httpMethod()),
                    nullToEmpty(log.path()),
                    nullToEmpty(log.result()),
                    nullToEmpty(log.errorCode()),
                    log.httpStatus() == null ? "" : String.valueOf(log.httpStatus()),
                    log.affectedCount() == null ? "" : String.valueOf(log.affectedCount()),
                    log.durationMs() == null ? "" : String.valueOf(log.durationMs()),
                    nullToEmpty(log.ip()),
                    nullToEmpty(log.userAgent()),
                    truncate(nullToEmpty(log.changes()), 4096),
                    nullToEmpty(log.traceId())));
        }
        CsvExporter.write(response, "oper-logs.csv",
                List.of("时间", "用户", "模块", "操作", "来源", "目标类型", "目标ID", "目标名称", "方法", "路径",
                        "结果", "错误码", "HTTP状态", "影响条数", "耗时(ms)", "IP", "UA", "变更", "traceId"),
                cells);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max) + "…";
    }
}
