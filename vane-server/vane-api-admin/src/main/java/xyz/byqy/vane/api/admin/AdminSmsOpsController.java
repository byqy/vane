package xyz.byqy.vane.api.admin;

import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import xyz.byqy.vane.audit.api.Audit;
import xyz.byqy.vane.system.application.SmsInboundService;
import xyz.byqy.vane.system.application.SmsOptoutService;
import xyz.byqy.vane.system.application.SmsReceiptService;
import xyz.byqy.vane.system.infrastructure.SmsInboundRepository;
import xyz.byqy.vane.system.infrastructure.SmsOptoutRepository;
import xyz.byqy.vane.system.infrastructure.SmsReceiptRepository;

/** 短信回执、上行与退订名单管理（ADR-022 P3）。 */
@RestController
@RequestMapping("/api/v1/admin/sms")
public class AdminSmsOpsController {

    private final SmsReceiptService receipts;
    private final SmsInboundService inbounds;
    private final SmsOptoutService optouts;

    public AdminSmsOpsController(SmsReceiptService receipts, SmsInboundService inbounds,
                                 SmsOptoutService optouts) {
        this.receipts = receipts;
        this.inbounds = inbounds;
        this.optouts = optouts;
    }

    @GetMapping("/logs/{logId}/receipts")
    @PreAuthorize("hasAuthority(T(xyz.byqy.vane.iam.domain.PermissionCatalog).OPS_SMS_LOG.code)")
    public java.util.List<SmsReceiptRepository.ReceiptRow> listReceipts(@PathVariable UUID logId) {
        return receipts.listByLog(logId);
    }

    @GetMapping("/inbounds")
    @PreAuthorize("hasAuthority(T(xyz.byqy.vane.iam.domain.PermissionCatalog).OPS_SMS_LOG.code)")
    public SmsInboundRepository.PageResult<SmsInboundRepository.InboundRow> pageInbounds(
            @RequestParam(name = "page[number]", defaultValue = "0") int pageNumber,
            @RequestParam(name = "page[size]", defaultValue = "20") int pageSize) {
        return inbounds.page(pageNumber, pageSize);
    }

    @GetMapping("/optouts")
    @PreAuthorize("hasAuthority(T(xyz.byqy.vane.iam.domain.PermissionCatalog).OPS_SMS_OPTOUT.code)")
    public SmsOptoutRepository.PageResult<SmsOptoutRepository.OptoutRow> pageOptouts(
            @RequestParam(name = "page[number]", defaultValue = "0") int pageNumber,
            @RequestParam(name = "page[size]", defaultValue = "20") int pageSize) {
        return optouts.page(pageNumber, pageSize);
    }

    @PostMapping("/optouts")
    @PreAuthorize("hasAuthority(T(xyz.byqy.vane.iam.domain.PermissionCatalog).OPS_SMS_OPTOUT.code)")
    @Audit(module = "sms", action = "create-optout")
    public ResponseEntity<Map<String, Boolean>> createOptout(@Valid @RequestBody CreateOptoutRequest request) {
        boolean created = optouts.create(request.phone(), request.scope(), request.reason());
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("created", created));
    }

    @DeleteMapping("/optouts/{id}")
    @PreAuthorize("hasAuthority(T(xyz.byqy.vane.iam.domain.PermissionCatalog).OPS_SMS_OPTOUT.code)")
    @Audit(module = "sms", action = "delete-optout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteOptout(@PathVariable UUID id) {
        optouts.delete(id);
    }

    public record CreateOptoutRequest(@NotBlank String phone, String scope, String reason) {
    }
}
