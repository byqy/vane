package xyz.byqy.vane.api.admin;

import java.net.URI;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import xyz.byqy.vane.audit.api.Audit;
import xyz.byqy.vane.system.application.SmsProviderService;
import xyz.byqy.vane.system.application.SmsProviderView;
import xyz.byqy.vane.system.application.SmsSendResult;

/** 短信服务商配置与测试发送（ADR-022，P1）。 */
@RestController
@RequestMapping("/api/v1/admin/sms")
public class AdminSmsController {

    private final SmsProviderService providers;

    public AdminSmsController(SmsProviderService providers) {
        this.providers = providers;
    }

    @GetMapping("/providers")
    @PreAuthorize("hasAuthority(T(xyz.byqy.vane.iam.domain.PermissionCatalog).OPS_SMS_CONFIG.code)")
    public List<SmsProviderView> list() {
        return providers.list();
    }

    @GetMapping("/providers/{code}")
    @PreAuthorize("hasAuthority(T(xyz.byqy.vane.iam.domain.PermissionCatalog).OPS_SMS_CONFIG.code)")
    public SmsProviderView get(@PathVariable String code) {
        return providers.get(code);
    }

    @PostMapping("/providers")
    @PreAuthorize("hasAuthority(T(xyz.byqy.vane.iam.domain.PermissionCatalog).OPS_SMS_CONFIG.code)")
    @Audit(module = "sms", action = "create-provider")
    public ResponseEntity<SmsProviderView> create(@Valid @RequestBody CreateProviderRequest request) {
        SmsProviderView created = providers.create(new SmsProviderService.CreateProvider(
                request.code(), request.type(), request.name(), request.config(), request.countries(),
                request.optoutKeywords(), request.enabled(), request.isDefault()));
        return ResponseEntity.created(URI.create("/api/v1/admin/sms/providers/" + created.code())).body(created);
    }

    @PatchMapping("/providers/{code}")
    @PreAuthorize("hasAuthority(T(xyz.byqy.vane.iam.domain.PermissionCatalog).OPS_SMS_CONFIG.code)")
    @Audit(module = "sms", action = "update-provider")
    public SmsProviderView update(@PathVariable String code, @Valid @RequestBody UpdateProviderRequest request) {
        return providers.update(code, new SmsProviderService.UpdateProvider(
                request.name(), request.config(), request.countries(), request.optoutKeywords(),
                request.enabled(), request.isDefault()));
    }

    @PostMapping("/providers/{code}/test")
    @PreAuthorize("hasAuthority(T(xyz.byqy.vane.iam.domain.PermissionCatalog).OPS_SMS_SEND.code)")
    @Audit(module = "sms", action = "test-send")
    public SmsSendResult test(@PathVariable String code, @Valid @RequestBody TestSendRequest request) {
        return providers.testSend(code, new SmsProviderService.TestSend(
                request.phone(), request.signature(), request.templateCode(), request.variables()));
    }

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
}
