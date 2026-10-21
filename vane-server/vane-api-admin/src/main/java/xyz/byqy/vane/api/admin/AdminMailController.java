package xyz.byqy.vane.api.admin;

import java.time.Instant;
import java.util.ArrayList;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import xyz.byqy.vane.api.support.CsvExporter;
import xyz.byqy.vane.audit.api.Audit;
import xyz.byqy.vane.common.audit.AuditContext;
import xyz.byqy.vane.system.application.MailLogQueryService;
import xyz.byqy.vane.system.application.MailProviderService;
import xyz.byqy.vane.system.application.MailProviderView;
import xyz.byqy.vane.system.application.MailSendService;
import xyz.byqy.vane.system.infrastructure.MailLogRepository.MailLogDetail;

/** 邮件服务（ADR-024）：SMTP 服务商、发送、发送日志。 */
@RestController
@RequestMapping("/api/v1/admin/mail")
public class AdminMailController {

    private final MailProviderService providers;
    private final MailSendService sender;
    private final MailLogQueryService logs;

    public AdminMailController(MailProviderService providers, MailSendService sender, MailLogQueryService logs) {
        this.providers = providers;
        this.sender = sender;
        this.logs = logs;
    }

    // ---------- 服务商 ----------

    @GetMapping("/providers")
    @PreAuthorize("hasAuthority(T(xyz.byqy.vane.iam.domain.PermissionCatalog).OPS_MAIL_PROVIDER.code)")
    public List<MailProviderView> listProviders() {
        return providers.list();
    }

    @PostMapping("/providers")
    @PreAuthorize("hasAuthority(T(xyz.byqy.vane.iam.domain.PermissionCatalog).OPS_MAIL_PROVIDER.code)")
    @Audit(module = "mail", action = "create-provider", targetType = "mail-provider",
            targetLabel = "#request.code()", detail = true)
    public ResponseEntity<MailProviderView> createProvider(@Valid @RequestBody ProviderRequest request) {
        MailProviderView created = providers.create(request.toCommand());
        return ResponseEntity.created(java.net.URI.create("/api/v1/admin/mail/providers/" + created.code()))
                .body(created);
    }

    @PatchMapping("/providers/{code}")
    @PreAuthorize("hasAuthority(T(xyz.byqy.vane.iam.domain.PermissionCatalog).OPS_MAIL_PROVIDER.code)")
    @Audit(module = "mail", action = "update-provider", targetType = "mail-provider",
            targetId = "#code", detail = true)
    public MailProviderView updateProvider(@PathVariable String code,
                                           @Valid @RequestBody ProviderRequest request) {
        return providers.update(code, request.toCommand());
    }

    @DeleteMapping("/providers/{code}")
    @PreAuthorize("hasAuthority(T(xyz.byqy.vane.iam.domain.PermissionCatalog).OPS_MAIL_PROVIDER.code)")
    @Audit(module = "mail", action = "delete-provider", targetType = "mail-provider",
            targetId = "#code", detail = true)
    public ResponseEntity<Void> deleteProvider(@PathVariable String code) {
        providers.delete(code);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/providers/{code}/verify")
    @PreAuthorize("hasAuthority(T(xyz.byqy.vane.iam.domain.PermissionCatalog).OPS_MAIL_PROVIDER.code)")
    @Audit(module = "mail", action = "verify-provider", targetType = "mail-provider", targetId = "#code")
    public VerifyResponse verifyProvider(@PathVariable String code) {
        providers.verify(code);
        return new VerifyResponse(true, "OK");
    }

    // ---------- 发送 ----------

    @PostMapping("/send")
    @PreAuthorize("hasAuthority(T(xyz.byqy.vane.iam.domain.PermissionCatalog).OPS_MAIL_SEND.code)")
    @Audit(module = "mail", action = "send", targetType = "mail-log")
    public ResponseEntity<MailSendService.Result> send(@Valid @RequestBody SendMailRequest request) {
        MailSendService.Result result = sender.send(request.templateCode(), request.to(), request.locale(),
                request.variables(), request.providerCode());
        AuditContext.setAffectedCount(1);
        return ResponseEntity.accepted().body(result);
    }

    // ---------- 日志 ----------

    @GetMapping("/logs")
    @PreAuthorize("hasAuthority(T(xyz.byqy.vane.iam.domain.PermissionCatalog).OPS_MAIL_LOG.code)")
    public MailLogQueryService.Page listLogs(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String templateCode,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) Instant timeFrom,
            @RequestParam(required = false) Instant timeTo,
            @RequestParam(name = "page[number]", defaultValue = "0") int pageNumber,
            @RequestParam(name = "page[size]", defaultValue = "20") int pageSize) {
        return logs.page(status, templateCode, to, timeFrom, timeTo, pageNumber, pageSize);
    }

    @GetMapping("/logs/{id}")
    @PreAuthorize("hasAuthority(T(xyz.byqy.vane.iam.domain.PermissionCatalog).OPS_MAIL_LOG.code)")
    public MailLogDetail logDetail(@PathVariable UUID id) {
        return logs.detail(id);
    }

    @GetMapping("/logs/export")
    @PreAuthorize("hasAuthority(T(xyz.byqy.vane.iam.domain.PermissionCatalog).OPS_MAIL_LOG_EXPORT.code)")
    @Audit(module = "mail", action = "export", targetType = "mail-log")
    public void exportLogs(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String templateCode,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) Instant timeFrom,
            @RequestParam(required = false) Instant timeTo,
            HttpServletResponse response) throws java.io.IOException {
        List<MailLogDetail> rows = logs.exportRows(status, templateCode, to, timeFrom, timeTo);
        AuditContext.setAffectedCount(rows.size());
        List<List<String>> cells = new ArrayList<>();
        for (MailLogDetail row : rows) {
            cells.add(List.of(
                    row.createdAt() == null ? "" : row.createdAt().toString(),
                    row.providerCode(),
                    row.templateCode(),
                    row.locale() == null ? "" : row.locale(),
                    row.toMasked(),
                    row.toDomain() == null ? "" : row.toDomain(),
                    row.subject() == null ? "" : row.subject(),
                    row.status(),
                    row.errorCode() == null ? "" : row.errorCode(),
                    String.valueOf(row.attempts()),
                    row.durationMs() == null ? "" : String.valueOf(row.durationMs()),
                    row.traceId() == null ? "" : row.traceId(),
                    row.sentAt() == null ? "" : row.sentAt().toString()));
        }
        CsvExporter.write(response, "mail-logs.csv",
                List.of("时间", "服务商", "模板", "语言", "收件人", "域名", "主题", "状态", "错误码",
                        "尝试次数", "耗时(ms)", "traceId", "发送时间"),
                cells);
    }

    // ---------- DTO ----------

    public record ProviderRequest(@NotBlank String code, @NotBlank String name, @NotBlank String host,
                                  int port, @NotBlank String tlsMode, String username, String password,
                                  @NotBlank String fromAddress, String fromName, boolean enabled,
                                  boolean isDefault, int dailyLimit, int timeoutMs) {

        MailProviderView.Command toCommand() {
            return new MailProviderView.Command(code, name, host, port, tlsMode, username, password,
                    fromAddress, fromName, enabled, isDefault,
                    dailyLimit <= 0 ? 500 : dailyLimit,
                    timeoutMs <= 0 ? 10000 : timeoutMs);
        }
    }

    public record SendMailRequest(@NotBlank String templateCode, @NotBlank String to, String locale,
                                  java.util.Map<String, String> variables, String providerCode) {
    }

    public record VerifyResponse(boolean ok, String message) {
    }
}
