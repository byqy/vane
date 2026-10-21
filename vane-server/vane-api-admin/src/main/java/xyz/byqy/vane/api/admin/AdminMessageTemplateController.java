package xyz.byqy.vane.api.admin;

import java.util.HashMap;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import xyz.byqy.vane.audit.api.Audit;
import xyz.byqy.vane.system.application.MailSendService;
import xyz.byqy.vane.system.application.MessageTemplateService;

/** 消息模板（ADM-11）：查询、编辑与测试发送（ADR-024 后发送入队，返回 202）。 */
@RestController
@RequestMapping("/api/v1/admin/message-templates")
public class AdminMessageTemplateController {

    private final MessageTemplateService templates;
    private final MailSendService mailSendService;

    public AdminMessageTemplateController(MessageTemplateService templates, MailSendService mailSendService) {
        this.templates = templates;
        this.mailSendService = mailSendService;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority(T(xyz.byqy.vane.iam.domain.PermissionCatalog).OPS_MAIL.code,"
            + "T(xyz.byqy.vane.iam.domain.PermissionCatalog).OPS_SMS.code)")
    public List<MessageTemplateService.TemplateView> list(@RequestParam(required = false) String channel) {
        return templates.list(channel);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority(T(xyz.byqy.vane.iam.domain.PermissionCatalog).OPS_MAIL.code)")
    @Audit(module = "mail", action = "update", targetType = "message-template", targetId = "#id")
    public MessageTemplateService.TemplateView update(@PathVariable java.util.UUID id,
                                                      @Valid @RequestBody UpdateTemplateRequest request) {
        List<MessageTemplateService.Translation> translations = request.translations().stream()
                .map(t -> new MessageTemplateService.Translation(t.locale(), t.subject(), t.body()))
                .toList();
        return templates.updateTranslations(id, translations);
    }

    @PostMapping("/{code}/send")
    @PreAuthorize("hasAuthority(T(xyz.byqy.vane.iam.domain.PermissionCatalog).OPS_MAIL_SEND.code)")
    @Audit(module = "mail", action = "send", targetType = "mail-log")
    public ResponseEntity<MailSendService.Result> send(@PathVariable String code,
                                                       @Valid @RequestBody SendRequest request) {
        Map<String, String> variables = request.variables() == null ? new HashMap<>() : request.variables();
        MailSendService.Result result = mailSendService.send(code, request.to(), request.locale(), variables,
                request.providerCode());
        xyz.byqy.vane.common.audit.AuditContext.setAffectedCount(1);
        return ResponseEntity.accepted().body(result);
    }

    public record UpdateTemplateRequest(List<TranslationRequest> translations) {
    }

    public record TranslationRequest(String locale, String subject, @NotBlank String body) {
    }

    public record SendRequest(@NotBlank String to, String locale, Map<String, String> variables,
                              String providerCode) {
    }
}
