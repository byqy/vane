package xyz.byqy.vane.api.admin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import xyz.byqy.vane.audit.api.Audit;
import xyz.byqy.vane.system.application.MessageTemplateService;

/** 消息模板与邮件发送（ADM-11）。 */
@RestController
@RequestMapping("/api/v1/admin/message-templates")
public class AdminMessageTemplateController {

    public record TranslationRequest(@NotBlank String locale, String subject, @NotBlank String body) {
    }

    public record UpdateTemplateRequest(@NotEmpty List<@Valid TranslationRequest> translations) {
    }

    public record SendRequest(@NotBlank @Email String to, String locale, String channel,
                              Map<String, String> variables) {
    }

    private final MessageTemplateService templates;

    public AdminMessageTemplateController(MessageTemplateService templates) {
        this.templates = templates;
    }

    @GetMapping
    @PreAuthorize("hasAuthority(T(xyz.byqy.vane.iam.domain.PermissionCatalog).OPS_MAIL.code)")
    public List<MessageTemplateService.TemplateView> list(@RequestParam(required = false) String channel) {
        return templates.list(channel);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority(T(xyz.byqy.vane.iam.domain.PermissionCatalog).OPS_MAIL.code)")
    @Audit(module = "mail", action = "update")
    public MessageTemplateService.TemplateView update(@PathVariable UUID id,
                                                      @Valid @RequestBody UpdateTemplateRequest request) {
        List<MessageTemplateService.Translation> translations = request.translations().stream()
                .map(t -> new MessageTemplateService.Translation(t.locale(), t.subject(), t.body()))
                .toList();
        return templates.updateTranslations(id, translations);
    }

    @PostMapping("/{code}/send")
    @PreAuthorize("hasAuthority(T(xyz.byqy.vane.iam.domain.PermissionCatalog).OPS_MAIL_SEND.code)")
    @Audit(module = "mail", action = "send")
    public MessageTemplateService.Rendered send(@PathVariable String code,
                                                @Valid @RequestBody SendRequest request) {
        Map<String, String> variables = request.variables() == null ? new HashMap<>() : request.variables();
        return templates.send(request.to(), code, request.channel() == null ? "EMAIL" : request.channel(),
                request.locale(), variables);
    }
}
