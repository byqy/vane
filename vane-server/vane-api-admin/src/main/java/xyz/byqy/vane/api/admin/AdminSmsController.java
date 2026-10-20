package xyz.byqy.vane.api.admin;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import xyz.byqy.vane.audit.api.Audit;
import xyz.byqy.vane.system.application.SmsLogQueryService;
import xyz.byqy.vane.system.application.SmsProviderService;
import xyz.byqy.vane.system.application.SmsProviderView;
import xyz.byqy.vane.system.application.SmsSendResult;
import xyz.byqy.vane.system.application.SmsSignatureService;
import xyz.byqy.vane.system.application.SmsTemplateBindingService;
import xyz.byqy.vane.system.infrastructure.SmsBindingRepository.BindingRow;
import xyz.byqy.vane.system.infrastructure.SmsLogRepository.PageResult;
import xyz.byqy.vane.system.infrastructure.SmsLogRepository.SmsLogView;
import xyz.byqy.vane.system.infrastructure.SmsSignatureRepository.SignatureRow;

/** 短信服务管理接口（ADR-022，P1）：服务商、签名、模板绑定、发送日志与测试发送。 */
@RestController
@RequestMapping("/api/v1/admin/sms")
public class AdminSmsController {

    private final SmsProviderService providers;
    private final SmsSignatureService signatures;
    private final SmsTemplateBindingService bindings;
    private final SmsLogQueryService logs;

    public AdminSmsController(SmsProviderService providers, SmsSignatureService signatures,
                              SmsTemplateBindingService bindings, SmsLogQueryService logs) {
        this.providers = providers;
        this.signatures = signatures;
        this.bindings = bindings;
        this.logs = logs;
    }

    // ---------- 服务商 ----------

    @GetMapping("/providers")
    @PreAuthorize("hasAuthority(T(xyz.byqy.vane.iam.domain.PermissionCatalog).OPS_SMS_CONFIG.code)")
    public List<SmsProviderView> listProviders() {
        return providers.list();
    }

    @GetMapping("/providers/{code}")
    @PreAuthorize("hasAuthority(T(xyz.byqy.vane.iam.domain.PermissionCatalog).OPS_SMS_CONFIG.code)")
    public SmsProviderView getProvider(@PathVariable String code) {
        return providers.get(code);
    }

    @PostMapping("/providers")
    @PreAuthorize("hasAuthority(T(xyz.byqy.vane.iam.domain.PermissionCatalog).OPS_SMS_CONFIG.code)")
    @Audit(module = "sms", action = "create-provider")
    public ResponseEntity<SmsProviderView> createProvider(@Valid @RequestBody CreateProviderRequest request) {
        SmsProviderView created = providers.create(new SmsProviderService.CreateProvider(
                request.code(), request.type(), request.name(), request.config(), request.countries(),
                request.optoutKeywords(), request.enabled(), request.isDefault()));
        return ResponseEntity.created(URI.create("/api/v1/admin/sms/providers/" + created.code())).body(created);
    }

    @PatchMapping("/providers/{code}")
    @PreAuthorize("hasAuthority(T(xyz.byqy.vane.iam.domain.PermissionCatalog).OPS_SMS_CONFIG.code)")
    @Audit(module = "sms", action = "update-provider")
    public SmsProviderView updateProvider(@PathVariable String code, @Valid @RequestBody UpdateProviderRequest request) {
        return providers.update(code, new SmsProviderService.UpdateProvider(
                request.name(), request.config(), request.countries(), request.optoutKeywords(),
                request.enabled(), request.isDefault()));
    }

    @PostMapping("/providers/{code}/test")
    @PreAuthorize("hasAuthority(T(xyz.byqy.vane.iam.domain.PermissionCatalog).OPS_SMS_SEND.code)")
    @Audit(module = "sms", action = "test-send")
    public SmsSendResult testSend(@PathVariable String code, @Valid @RequestBody TestSendRequest request) {
        return providers.testSend(code, new SmsProviderService.TestSend(
                request.phone(), request.signature(), request.templateCode(), request.variables()));
    }

    // ---------- 签名 ----------

    @GetMapping("/signatures")
    @PreAuthorize("hasAuthority(T(xyz.byqy.vane.iam.domain.PermissionCatalog).OPS_SMS_CONFIG.code)")
    public List<SignatureRow> listSignatures(@RequestParam(required = false) String provider) {
        return signatures.list(provider);
    }

    @PostMapping("/signatures")
    @PreAuthorize("hasAuthority(T(xyz.byqy.vane.iam.domain.PermissionCatalog).OPS_SMS_CONFIG.code)")
    @Audit(module = "sms", action = "create-signature")
    public ResponseEntity<SignatureRow> createSignature(@Valid @RequestBody CreateSignatureRequest request) {
        SignatureRow created = signatures.create(new SmsSignatureService.CreateSignature(
                request.providerCode(), request.signName(), request.countryCode(), request.locale(),
                request.status(), request.approvalStatus()));
        return ResponseEntity.created(URI.create("/api/v1/admin/sms/signatures/" + created.id())).body(created);
    }

    @PatchMapping("/signatures/{id}")
    @PreAuthorize("hasAuthority(T(xyz.byqy.vane.iam.domain.PermissionCatalog).OPS_SMS_CONFIG.code)")
    @Audit(module = "sms", action = "update-signature")
    public SignatureRow updateSignature(@PathVariable UUID id, @Valid @RequestBody UpdateSignatureRequest request) {
        return signatures.update(id, new SmsSignatureService.UpdateSignature(
                request.signName(), request.countryCode(), request.locale(), request.status(),
                request.approvalStatus()));
    }

    // ---------- 模板绑定 ----------

    @GetMapping("/template-bindings")
    @PreAuthorize("hasAuthority(T(xyz.byqy.vane.iam.domain.PermissionCatalog).OPS_SMS_TEMPLATE.code)")
    public List<BindingRow> listBindings(@RequestParam(required = false) String provider) {
        return bindings.list(provider);
    }

    @GetMapping("/template-bindings/{templateId}")
    @PreAuthorize("hasAuthority(T(xyz.byqy.vane.iam.domain.PermissionCatalog).OPS_SMS_TEMPLATE.code)")
    public List<BindingRow> listTemplateBindings(@PathVariable UUID templateId) {
        return bindings.listByTemplate(templateId);
    }

    @PutMapping("/template-bindings/{templateId}")
    @PreAuthorize("hasAuthority(T(xyz.byqy.vane.iam.domain.PermissionCatalog).OPS_SMS_TEMPLATE.code)")
    @Audit(module = "sms", action = "replace-bindings")
    public ResponseEntity<List<BindingRow>> replaceBindings(@PathVariable UUID templateId,
                                                            @Valid @RequestBody ReplaceBindingsRequest request) {
        List<SmsTemplateBindingService.BindingSpec> specs = request.bindings().stream()
                .map(spec -> new SmsTemplateBindingService.BindingSpec(spec.providerCode(),
                        spec.providerTemplateCode(), spec.signatureId(), spec.countryCode(), spec.locale(),
                        spec.paramMapping()))
                .toList();
        return ResponseEntity.ok(bindings.replace(templateId, specs));
    }

    // ---------- 发送日志 ----------

    @GetMapping("/logs")
    @PreAuthorize("hasAuthority(T(xyz.byqy.vane.iam.domain.PermissionCatalog).OPS_SMS_LOG.code)")
    public PageResult<SmsLogView> pageLogs(
            @RequestParam(name = "page[number]", defaultValue = "0") int pageNumber,
            @RequestParam(name = "page[size]", defaultValue = "20") int pageSize,
            @RequestParam(required = false) String templateCode,
            @RequestParam(required = false) String providerCode,
            @RequestParam(required = false) String result,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return logs.page(templateCode, providerCode, result, from, to, pageNumber, pageSize);
    }

    // ---------- 请求 DTO ----------

    public record CreateProviderRequest(@NotBlank String code, @NotBlank String type, @NotBlank String name,
                                        @NotNull Map<String, String> config,
                                        @NotNull List<String> countries, List<String> optoutKeywords,
                                        Boolean enabled, Boolean isDefault) {
    }

    public record UpdateProviderRequest(String name, Map<String, String> config, List<String> countries,
                                        List<String> optoutKeywords, Boolean enabled, Boolean isDefault) {
    }

    public record TestSendRequest(@NotBlank String phone, @NotBlank String signature,
                                  @NotBlank String templateCode, Map<String, String> variables) {
    }

    public record CreateSignatureRequest(@NotBlank String providerCode, @NotBlank String signName,
                                         @NotBlank String countryCode, @NotBlank String locale,
                                         String status, String approvalStatus) {
    }

    public record UpdateSignatureRequest(String signName, String countryCode, String locale, String status,
                                         String approvalStatus) {
    }

    public record ReplaceBindingsRequest(@NotNull List<BindingSpecRequest> bindings) {
    }

    public record BindingSpecRequest(@NotBlank String providerCode, @NotBlank String providerTemplateCode,
                                     UUID signatureId, @NotBlank String countryCode, @NotBlank String locale,
                                     Map<String, String> paramMapping) {
    }
}
