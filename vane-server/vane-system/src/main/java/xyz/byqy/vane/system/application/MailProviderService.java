package xyz.byqy.vane.system.application;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import xyz.byqy.vane.common.audit.AuditContext;
import xyz.byqy.vane.common.error.ApiException;
import xyz.byqy.vane.common.error.CommonErrors;
import xyz.byqy.vane.system.infrastructure.MailDispatcher;
import xyz.byqy.vane.system.infrastructure.MailProviderRepository;
import xyz.byqy.vane.system.infrastructure.MailProviderRepository.MailProviderRow;
import xyz.byqy.vane.system.infrastructure.SecretCipher;

/** 邮件服务商配置（ADR-024）：SMTP 参数、密钥加密、唯一默认与连通性校验。 */
@Service
public class MailProviderService {

    private static final Pattern CODE = Pattern.compile("[a-z0-9][a-z0-9-]{1,31}");
    private static final Pattern TLS = Pattern.compile("NONE|STARTTLS|SSL");
    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Set<String> TLS_MODES = Set.of("NONE", "STARTTLS", "SSL");

    private final MailProviderRepository providers;
    private final SecretCipher cipher;
    private final MailDispatcher dispatcher;

    public MailProviderService(MailProviderRepository providers, SecretCipher cipher, MailDispatcher dispatcher) {
        this.providers = providers;
        this.cipher = cipher;
        this.dispatcher = dispatcher;
    }

    @Transactional(readOnly = true)
    public List<MailProviderView> list() {
        return providers.findAll().stream().map(MailProviderView::of).toList();
    }

    @Transactional
    public MailProviderView create(MailProviderView.Command command) {
        validate(command, true);
        if (providers.findByCode(command.code()).isPresent()) {
            throw new ApiException(CommonErrors.MAIL_PROVIDER_CODE_TAKEN);
        }
        boolean first = providers.count() == 0;
        boolean makeDefault = command.isDefault() || first;
        String cipherText = null;
        String keyVersion = null;
        if (command.password() != null && !command.password().isBlank()) {
            requireCipher();
            cipherText = cipher.encrypt("sys_mail_provider:" + command.code(), command.password());
            keyVersion = cipher.activeVersion();
        }
        if (makeDefault) {
            providers.clearDefaultExcept(command.code());
        }
        MailProviderRow row = toRow(command, cipherText, keyVersion, command.enabled(), makeDefault);
        providers.insert(row);
        AuditContext.recordCreated(fields(row), "name", "host", "port", "tlsMode", "fromAddress",
                "enabled", "isDefault", "dailyLimit");
        return MailProviderView.of(row);
    }

    @Transactional
    public MailProviderView update(String code, MailProviderView.Command command) {
        MailProviderRow existing = providers.findByCode(code)
                .orElseThrow(() -> new ApiException(CommonErrors.MAIL_PROVIDER_NOT_FOUND));
        validate(command, false);
        boolean disablingDefault = existing.isDefault() && !command.enabled();
        boolean clearingDefault = existing.isDefault() && !command.isDefault();
        if (disablingDefault || clearingDefault) {
            throw new ApiException(CommonErrors.MAIL_PROVIDER_IN_USE);
        }
        String cipherText = existing.passwordCipher();
        String keyVersion = existing.keyVersion();
        if (command.password() != null && !command.password().isBlank()) {
            requireCipher();
            cipherText = cipher.encrypt("sys_mail_provider:" + code, command.password());
            keyVersion = cipher.activeVersion();
        }
        if (command.isDefault() && !existing.isDefault()) {
            providers.clearDefaultExcept(code);
        }
        MailProviderRow row = new MailProviderRow(code, command.name(), command.host(), command.port(),
                command.tlsMode(), command.username(), cipherText, keyVersion, command.fromAddress(),
                command.fromName(), command.enabled(), command.isDefault(), command.dailyLimit(),
                command.timeoutMs());
        providers.update(row);
        AuditContext.record(fields(existing), fields(row), "name", "host", "port", "tlsMode",
                "fromAddress", "enabled", "isDefault", "dailyLimit");
        return MailProviderView.of(row);
    }

    @Transactional
    public void delete(String code) {
        MailProviderRow existing = providers.findByCode(code)
                .orElseThrow(() -> new ApiException(CommonErrors.MAIL_PROVIDER_NOT_FOUND));
        if (existing.isDefault()) {
            throw new ApiException(CommonErrors.MAIL_PROVIDER_IN_USE);
        }
        if (existing.enabled() && providers.findAll().stream()
                .filter(MailProviderRow::enabled).count() == 1) {
            throw new ApiException(CommonErrors.MAIL_PROVIDER_IN_USE);
        }
        providers.delete(code);
        AuditContext.recordDeleted(fields(existing), "name", "host", "port", "fromAddress");
    }

    /** 连通性与认证校验：不发送邮件，只建立连接。 */
    public void verify(String code) {
        MailProviderRow row = providers.findByCode(code)
                .orElseThrow(() -> new ApiException(CommonErrors.MAIL_PROVIDER_NOT_FOUND));
        try {
            dispatcher.verify(settings(row));
        } catch (RuntimeException ex) {
            throw new ApiException(CommonErrors.MAIL_PROVIDER_UNREACHABLE);
        }
    }

    /** 供发送侧使用：解密后的 SMTP 参数；未配置密钥时抛 MAIL.NOT_CONFIGURED。 */
    public MailPort.SmtpSettings smtpSettings(MailProviderRow row) {
        return settings(row);
    }

    private MailPort.SmtpSettings settings(MailProviderRow row) {
        String password = null;
        if (row.passwordCipher() != null) {
            if (!cipher.hasKey(row.keyVersion())) {
                throw new ApiException(CommonErrors.MAIL_NOT_CONFIGURED);
            }
            password = cipher.decrypt(row.keyVersion(), "sys_mail_provider:" + row.code(),
                    row.passwordCipher());
        }
        return new MailPort.SmtpSettings(row.host(), row.port(), row.tlsMode(), row.username(), password,
                row.fromAddress(), row.fromName(), row.timeoutMs());
    }

    private void requireCipher() {
        if (!cipher.configured()) {
            throw new ApiException(CommonErrors.MAIL_NOT_CONFIGURED);
        }
    }

    private static void validate(MailProviderView.Command command, boolean requireCode) {
        if (requireCode && (command.code() == null || !CODE.matcher(command.code()).matches())) {
            throw new ApiException(CommonErrors.VALIDATION_FAILED);
        }
        if (command.name() == null || command.name().isBlank()
                || command.host() == null || command.host().isBlank()
                || command.port() < 1 || command.port() > 65535
                || command.tlsMode() == null || !TLS_MODES.contains(command.tlsMode())
                || command.fromAddress() == null || !EMAIL.matcher(command.fromAddress()).matches()
                || command.fromAddress().length() > 254
                || command.dailyLimit() < 1
                || command.timeoutMs() < 1000 || command.timeoutMs() > 120_000) {
            throw new ApiException(CommonErrors.VALIDATION_FAILED);
        }
        if (TLS.matcher(command.tlsMode()).matches() && command.tlsMode().isBlank()) {
            throw new ApiException(CommonErrors.VALIDATION_FAILED);
        }
    }

    private static MailProviderRow toRow(MailProviderView.Command command, String cipherText,
                                         String keyVersion, boolean enabled, boolean isDefault) {
        return new MailProviderRow(command.code(), command.name(), command.host(), command.port(),
                command.tlsMode(), command.username(), cipherText, keyVersion, command.fromAddress(),
                command.fromName(), enabled, isDefault, command.dailyLimit(), command.timeoutMs());
    }

    private static Map<String, Object> fields(MailProviderRow row) {
        return Map.of("name", row.name(), "host", row.host(), "port", row.port(),
                "tlsMode", row.tlsMode(), "fromAddress", row.fromAddress(), "enabled", row.enabled(),
                "isDefault", row.isDefault(), "dailyLimit", row.dailyLimit());
    }
}
