package xyz.byqy.vane.system.application;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import xyz.byqy.vane.common.error.CommonErrors;
import xyz.byqy.vane.system.infrastructure.MailLogRepository;
import xyz.byqy.vane.system.infrastructure.MailLogRepository.MailQueueRow;
import xyz.byqy.vane.system.infrastructure.MailProviderRepository;
import xyz.byqy.vane.system.infrastructure.MailProviderRepository.MailProviderRow;
import xyz.byqy.vane.system.infrastructure.SecretCipher;

/** 内置任务：邮件发送 worker（ADR-024）。至少一次投递；失败按可重试性退避或终结。 */
@Component
public class MailWorkerHandler implements JobHandler {

    private static final Logger log = LoggerFactory.getLogger(MailWorkerHandler.class);
    private static final String LOCK_KEY = "vane:mail:send:lock";
    private static final int BATCH = 50;
    private static final int MAX_ATTEMPTS = 3;
    private static final Duration LOCK_TTL = Duration.ofSeconds(60);

    private final MailLogRepository logs;
    private final MailProviderRepository providers;
    private final MailSendService sender;
    private final MailPort mailPort;
    private final SecretCipher cipher;
    private final ObjectMapper json;
    private final StringRedisTemplate redis;

    public MailWorkerHandler(MailLogRepository logs, MailProviderRepository providers,
                             MailSendService sender, MailPort mailPort, SecretCipher cipher,
                             ObjectMapper json, StringRedisTemplate redis) {
        this.logs = logs;
        this.providers = providers;
        this.sender = sender;
        this.mailPort = mailPort;
        this.cipher = cipher;
        this.json = json;
        this.redis = redis;
    }

    @Override
    public String name() {
        return "mail-send-worker";
    }

    @Override
    public void run(String params) {
        if (!JobLocks.tryAcquire(redis, LOCK_KEY, LOCK_TTL)) {
            return;
        }
        try {
            for (MailQueueRow row : logs.claim(BATCH)) {
                sendOne(row);
            }
        } finally {
            JobLocks.release(redis, LOCK_KEY);
        }
    }

    private void sendOne(MailQueueRow row) {
        long startedAt = System.nanoTime();
        try {
            Map<String, String> variables = readVariables(row.variablesJson());
            MessageTemplateService.Rendered rendered = sender.render(row.templateCode(), row.locale(), variables);
            if (sender.loggingMode()) {
                logs.markSent(row.id(), rendered.subject());
                return;
            }
            MailProviderRow provider = providers.findByCode(row.providerCode()).orElse(null);
            if (provider == null || !provider.enabled()) {
                logs.markFailed(row.id(), row.attempts() + 1, CommonErrors.MAIL_NOT_CONFIGURED.code(), null);
                return;
            }
            String recipient = cipher.decrypt(row.toKeyVersion(), "sys_mail_log:" + row.id(), row.toCipher());
            mailPort.send(providerSettings(provider), recipient, rendered.subject(), rendered.body());
            logs.markSent(row.id(), rendered.subject());
            log.debug("mail log {} sent in {} ms", row.id(), elapsed(startedAt));
        } catch (RuntimeException ex) {
            handleFailure(row, ex);
        }
    }

    private MailPort.SmtpSettings providerSettings(MailProviderRow provider) {
        String password = null;
        if (provider.passwordCipher() != null) {
            password = cipher.decrypt(provider.keyVersion(), "sys_mail_provider:" + provider.code(),
                    provider.passwordCipher());
        }
        return new MailPort.SmtpSettings(provider.host(), provider.port(), provider.tlsMode(),
                provider.username(), password, provider.fromAddress(), provider.fromName(),
                provider.timeoutMs());
    }

    private void handleFailure(MailQueueRow row, RuntimeException ex) {
        int attempts = row.attempts() + 1;
        String detail = ex.getMessage();
        if (attempts >= MAX_ATTEMPTS || permanent(ex)) {
            logs.markFailed(row.id(), attempts, CommonErrors.MAIL_SEND_FAILED.code(), detail);
            log.warn("mail log {} failed after {} attempts: {}", row.id(), attempts, detail);
            return;
        }
        Instant next = Instant.now().plus(backoff(attempts));
        logs.markRetry(row.id(), attempts, next, CommonErrors.MAIL_SEND_FAILED.code(), detail);
        log.info("mail log {} retry #{} at {}", row.id(), attempts, next);
    }

    private static boolean permanent(RuntimeException ex) {
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (cause instanceof jakarta.mail.AuthenticationFailedException) {
                return true;
            }
            String message = cause.getMessage();
            if (message != null && (message.contains(" 535") || message.contains(" 530")
                    || message.contains(" 550") || message.contains(" 551") || message.contains(" 553"))) {
                return true;
            }
        }
        return false;
    }

    private static Duration backoff(int attempts) {
        return switch (attempts) {
            case 1 -> Duration.ofMinutes(1);
            case 2 -> Duration.ofMinutes(5);
            default -> Duration.ofMinutes(15);
        };
    }

    private Map<String, String> readVariables(String raw) {
        if (raw == null || raw.isBlank()) {
            return Map.of();
        }
        try {
            return json.readValue(raw, new TypeReference<Map<String, String>>() {
            });
        } catch (RuntimeException ex) {
            return Map.of();
        }
    }

    private static long elapsed(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }
}
