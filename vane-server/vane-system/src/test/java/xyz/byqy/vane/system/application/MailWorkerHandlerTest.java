package xyz.byqy.vane.system.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import tools.jackson.databind.json.JsonMapper;
import xyz.byqy.vane.system.infrastructure.MailLogRepository;
import xyz.byqy.vane.system.infrastructure.MailLogRepository.MailQueueRow;
import xyz.byqy.vane.system.infrastructure.MailProviderRepository;
import xyz.byqy.vane.system.infrastructure.MailProviderRepository.MailProviderRow;
import xyz.byqy.vane.system.infrastructure.SecretCipher;

class MailWorkerHandlerTest {

    private final MailLogRepository logs = mock(MailLogRepository.class);
    private final MailProviderRepository providers = mock(MailProviderRepository.class);
    private final MailSendService sender = mock(MailSendService.class);
    private final MailPort mailPort = mock(MailPort.class);
    private final SecretCipher cipher = mock(SecretCipher.class);
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> valueOps = mock(ValueOperations.class);

    private final UUID logId = UUID.randomUUID();
    private final MailQueueRow row = new MailQueueRow(logId, "smtp", "user.welcome", "en",
            "cipher", "1", "{\"username\":\"IT\"}", 0);

    private MailWorkerHandler handler() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), anyString(), any(java.time.Duration.class))).thenReturn(true);
        return new MailWorkerHandler(logs, providers, sender, mailPort, cipher,
                JsonMapper.builder().build(), redis);
    }

    @Test
    void loggingModeMarksSentWithoutTouchingProvider() {
        MailWorkerHandler handler = handler();
        when(logs.claim(50)).thenReturn(List.of(row));
        when(sender.loggingMode()).thenReturn(true);
        when(sender.render(eq("user.welcome"), eq("en"), any()))
                .thenReturn(new MessageTemplateService.Rendered("user.welcome", "EMAIL", "en",
                        "Welcome", "Hello IT"));

        handler.run(null);

        verify(logs).markSent(logId, "Welcome");
        verify(mailPort, never()).send(any(), anyString(), anyString(), anyString());
        verify(redis).delete("vane:mail:send:lock");
    }

    @Test
    void retryableFailureSchedulesRetry() {
        MailWorkerHandler handler = handler();
        when(logs.claim(50)).thenReturn(List.of(row));
        when(sender.loggingMode()).thenReturn(false);
        when(sender.render(anyString(), anyString(), any())).thenReturn(
                new MessageTemplateService.Rendered("user.welcome", "EMAIL", "en", "Welcome", "Hello IT"));
        when(providers.findByCode("smtp")).thenReturn(Optional.of(provider()));
        when(cipher.decrypt(eq("1"), anyString(), anyString())).thenReturn("it@example.com");
        doThrow(new IllegalStateException("connect timed out")).when(mailPort)
                .send(any(), anyString(), anyString(), anyString());

        handler.run(null);

        verify(logs).markRetry(eq(logId), eq(1), any(), eq("MAIL.SEND.FAILED"), anyString());
        verify(logs, never()).markFailed(any(), anyInt(), anyString(), any());
    }

    @Test
    void permanentFailureMarksFailedImmediately() {
        MailWorkerHandler handler = handler();
        when(logs.claim(50)).thenReturn(List.of(row));
        when(sender.loggingMode()).thenReturn(false);
        when(sender.render(anyString(), anyString(), any())).thenReturn(
                new MessageTemplateService.Rendered("user.welcome", "EMAIL", "en", "Welcome", "Hello IT"));
        when(providers.findByCode("smtp")).thenReturn(Optional.of(provider()));
        when(cipher.decrypt(eq("1"), anyString(), anyString())).thenReturn("it@example.com");
        doThrow(new IllegalStateException("SMTP 550 mailbox unavailable")).when(mailPort)
                .send(any(), anyString(), anyString(), anyString());

        handler.run(null);

        verify(logs).markFailed(eq(logId), eq(1), eq("MAIL.SEND.FAILED"), anyString());
        verify(logs, never()).markRetry(any(), anyInt(), any(), anyString(), any());
    }

    @Test
    void lockHeldSkipsRound() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), anyString(), any(java.time.Duration.class))).thenReturn(false);
        MailWorkerHandler handler = new MailWorkerHandler(logs, providers, sender, mailPort, cipher,
                JsonMapper.builder().build(), redis);

        handler.run(null);

        verify(logs, never()).claim(anyInt());
    }

    private static MailProviderRow provider() {
        return new MailProviderRow("smtp", "SMTP", "smtp.example.com", 587, "STARTTLS", "user",
                "cipher", "1", "no-reply@example.com", "vane", true, true, 500, 10000);
    }
}
