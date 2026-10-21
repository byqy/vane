package xyz.byqy.vane.system.application;

import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.ObjectMapper;
import xyz.byqy.vane.common.error.ApiException;
import xyz.byqy.vane.common.error.CommonErrors;
import xyz.byqy.vane.system.infrastructure.MailLogRepository;
import xyz.byqy.vane.system.infrastructure.MailLogRepository.MailQueueEntry;
import xyz.byqy.vane.system.infrastructure.MailProviderRepository;
import xyz.byqy.vane.system.infrastructure.MailProviderRepository.MailProviderRow;
import xyz.byqy.vane.system.infrastructure.SecretCipher;

/** 邮件发送入口（ADR-024）：校验 → 渲染 → 入队；实际投递由 worker 完成。 */
@Service
public class MailSendService {

    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{[A-Za-z0-9_]+}");
    private static final String LOGGING_PROVIDER = "logging";

    private final MailProviderRepository providers;
    private final MailLogRepository logs;
    private final MessageTemplateService templates;
    private final SecretCipher cipher;
    private final ObjectMapper json;
    private final String mode;

    public MailSendService(MailProviderRepository providers, MailLogRepository logs,
                           MessageTemplateService templates, SecretCipher cipher, ObjectMapper json,
                           @Value("${vane.mail.mode:smtp}") String mode) {
        this.providers = providers;
        this.logs = logs;
        this.templates = templates;
        this.cipher = cipher;
        this.json = json;
        this.mode = mode == null ? "smtp" : mode.strip();
    }

    public boolean loggingMode() {
        return "logging".equalsIgnoreCase(mode);
    }

    @Transactional
    public Result send(String templateCode, String to, String locale, Map<String, String> variables,
                       String providerCode) {
        String recipient = to == null ? "" : to.strip().toLowerCase();
        if (recipient.length() > 254 || !EMAIL.matcher(recipient).matches()) {
            throw new ApiException(CommonErrors.MAIL_RECIPIENT_INVALID);
        }
        MailProviderRow provider = null;
        String effectiveProvider = LOGGING_PROVIDER;
        if (!loggingMode()) {
            provider = resolveProvider(providerCode);
            effectiveProvider = provider.code();
            assertQuota(provider);
        }
        MessageTemplateService.Rendered rendered = render(templateCode, locale, variables);
        UUID id = UUID.randomUUID();
        String toCipher = null;
        String keyVersion = null;
        String toHash = cipher.configured() ? cipher.hash(recipient) : null;
        if (provider != null) {
            requireCipher();
            toCipher = cipher.encrypt("sys_mail_log:" + id, recipient);
            keyVersion = cipher.activeVersion();
        }
        logs.enqueue(new MailQueueEntry(id, effectiveProvider, templateCode, locale, mask(recipient),
                domain(recipient), toCipher, keyVersion, toHash, writeVariables(variables)));
        return new Result(id, "QUEUED", rendered.subject(), rendered.body());
    }

    /** worker 复用：渲染并检查变量是否齐全。 */
    public MessageTemplateService.Rendered render(String templateCode, String locale,
                                                  Map<String, String> variables) {
        MessageTemplateService.Rendered rendered = templates.render(templateCode, "EMAIL", locale, variables);
        if (hasPlaceholder(rendered.subject()) || hasPlaceholder(rendered.body())) {
            throw new ApiException(CommonErrors.MAIL_TEMPLATE_VARIABLE_MISSING);
        }
        return rendered;
    }

    private MailProviderRow resolveProvider(String providerCode) {
        MailProviderRow provider = providerCode == null || providerCode.isBlank()
                ? providers.findDefault().orElse(null)
                : providers.findByCode(providerCode)
                        .orElseThrow(() -> new ApiException(CommonErrors.MAIL_PROVIDER_NOT_FOUND));
        if (provider == null || !provider.enabled()) {
            throw new ApiException(CommonErrors.MAIL_NOT_CONFIGURED);
        }
        return provider;
    }

    private void assertQuota(MailProviderRow provider) {
        if (logs.countSentToday(provider.code()) >= provider.dailyLimit()) {
            throw new ApiException(CommonErrors.MAIL_QUOTA_EXCEEDED);
        }
    }

    private void requireCipher() {
        if (!cipher.configured()) {
            throw new ApiException(CommonErrors.MAIL_NOT_CONFIGURED);
        }
    }

    private String writeVariables(Map<String, String> variables) {
        try {
            return json.writeValueAsString(variables == null ? Map.of() : variables);
        } catch (RuntimeException ex) {
            throw new ApiException(CommonErrors.VALIDATION_FAILED);
        }
    }

    private static boolean hasPlaceholder(String value) {
        return value != null && PLACEHOLDER.matcher(value).find();
    }

    static String mask(String address) {
        int at = address.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        String local = address.substring(0, at);
        String masked = local.length() <= 1 ? "*" : local.charAt(0) + "***";
        return masked + address.substring(at);
    }

    static String domain(String address) {
        int at = address.indexOf('@');
        return at < 0 ? null : address.substring(at + 1);
    }

    public record Result(UUID logId, String status, String subject, String body) {
    }
}
