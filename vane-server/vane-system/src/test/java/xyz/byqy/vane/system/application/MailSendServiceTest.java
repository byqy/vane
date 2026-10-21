package xyz.byqy.vane.system.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;
import xyz.byqy.vane.common.error.ApiException;
import xyz.byqy.vane.common.error.CommonErrors;
import xyz.byqy.vane.system.infrastructure.MailLogRepository;
import xyz.byqy.vane.system.infrastructure.MailProviderRepository;
import xyz.byqy.vane.system.infrastructure.MailProviderRepository.MailProviderRow;
import xyz.byqy.vane.system.infrastructure.SecretCipher;

class MailSendServiceTest {

    private final MailProviderRepository providers = mock(MailProviderRepository.class);
    private final MailLogRepository logs = mock(MailLogRepository.class);
    private final MessageTemplateService templates = mock(MessageTemplateService.class);
    private final SecretCipher cipher = mock(SecretCipher.class);

    private MailSendService service(String mode) {
        return new MailSendService(providers, logs, templates, cipher, JsonMapper.builder().build(), mode);
    }

    private static MailProviderRow provider() {
        return new MailProviderRow("smtp", "SMTP", "smtp.example.com", 587, "STARTTLS", "user",
                "cipher", "1", "no-reply@example.com", "vane", true, true, 500, 10000);
    }

    @Test
    void loggingModeEnqueuesWithoutProvider() {
        when(templates.render("user.welcome", "EMAIL", "en", Map.of("username", "IT")))
                .thenReturn(new MessageTemplateService.Rendered("user.welcome", "EMAIL", "en",
                        "Welcome", "Hello IT"));

        MailSendService.Result result = service("logging").send("user.welcome", "it@example.com", "en",
                Map.of("username", "IT"), null);

        assertThat(result.status()).isEqualTo("QUEUED");
        assertThat(result.body()).isEqualTo("Hello IT");
        verify(logs).enqueue(any(MailLogRepository.MailQueueEntry.class));
        verify(providers, never()).findDefault();
    }

    @Test
    void invalidRecipientIsRejected() {
        assertThatThrownBy(() -> service("logging").send("user.welcome", "not-an-email", "en", Map.of(), null))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).errorCode())
                .isEqualTo(CommonErrors.MAIL_RECIPIENT_INVALID);
    }

    @Test
    void missingVariablesAreRejected() {
        when(templates.render(anyString(), anyString(), any(), any()))
                .thenReturn(new MessageTemplateService.Rendered("user.welcome", "EMAIL", "en",
                        "Welcome", "Hello {username}"));

        assertThatThrownBy(() -> service("logging").send("user.welcome", "it@example.com", "en", Map.of(), null))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).errorCode())
                .isEqualTo(CommonErrors.MAIL_TEMPLATE_VARIABLE_MISSING);
    }

    @Test
    void quotaIsEnforcedPerProvider() {
        when(providers.findDefault()).thenReturn(Optional.of(provider()));
        when(logs.countSentToday("smtp")).thenReturn(500);
        when(templates.render(anyString(), anyString(), any(), any()))
                .thenReturn(new MessageTemplateService.Rendered("user.welcome", "EMAIL", "en",
                        "Welcome", "Hello IT"));

        assertThatThrownBy(() -> service("smtp").send("user.welcome", "it@example.com", "en",
                Map.of("username", "IT"), null))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).errorCode())
                .isEqualTo(CommonErrors.MAIL_QUOTA_EXCEEDED);
        verify(logs, never()).enqueue(any(MailLogRepository.MailQueueEntry.class));
    }

    @Test
    void smtpModeWithoutProviderIsNotConfigured() {
        when(providers.findDefault()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service("smtp").send("user.welcome", "it@example.com", "en", Map.of(), null))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).errorCode())
                .isEqualTo(CommonErrors.MAIL_NOT_CONFIGURED);
    }

    @Test
    void maskKeepsFirstCharacterAndDomain() {
        assertThat(MailSendService.mask("alice@example.com")).isEqualTo("a***@example.com");
        assertThat(MailSendService.domain("alice@example.com")).isEqualTo("example.com");
    }

    @Test
    void smtpModeEncryptsRecipientWithLogAad() {
        when(providers.findDefault()).thenReturn(Optional.of(provider()));
        when(logs.countSentToday("smtp")).thenReturn(0);
        when(cipher.configured()).thenReturn(true);
        when(cipher.encrypt(anyString(), eq("it@example.com"))).thenReturn("recipient-cipher");
        when(cipher.activeVersion()).thenReturn("1");
        when(cipher.hash("it@example.com")).thenReturn("hash");
        when(templates.render(anyString(), anyString(), any(), any()))
                .thenReturn(new MessageTemplateService.Rendered("user.welcome", "EMAIL", "en",
                        "Welcome", "Hello IT"));

        service("smtp").send("user.welcome", "IT@example.com", "en", Map.of("username", "IT"), null);

        verify(cipher).encrypt(anyString(), eq("it@example.com"));
        verify(logs).enqueue(any(MailLogRepository.MailQueueEntry.class));
    }
}
