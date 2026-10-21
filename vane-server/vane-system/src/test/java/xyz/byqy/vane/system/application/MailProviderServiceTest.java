package xyz.byqy.vane.system.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import xyz.byqy.vane.common.error.ApiException;
import xyz.byqy.vane.common.error.CommonErrors;
import xyz.byqy.vane.system.infrastructure.MailDispatcher;
import xyz.byqy.vane.system.infrastructure.MailProviderRepository;
import xyz.byqy.vane.system.infrastructure.MailProviderRepository.MailProviderRow;
import xyz.byqy.vane.system.infrastructure.SecretCipher;

class MailProviderServiceTest {

    private final MailProviderRepository providers = mock(MailProviderRepository.class);
    private final SecretCipher cipher = mock(SecretCipher.class);
    private final MailDispatcher dispatcher = mock(MailDispatcher.class);
    private final MailProviderService service = new MailProviderService(providers, cipher, dispatcher);

    private static MailProviderView.Command command(boolean enabled, boolean isDefault) {
        return new MailProviderView.Command("smtp", "SMTP", "smtp.example.com", 587, "STARTTLS",
                "user", "secret", "no-reply@example.com", "vane", enabled, isDefault, 500, 10000);
    }

    private static MailProviderRow row(boolean enabled, boolean isDefault) {
        return new MailProviderRow("smtp", "SMTP", "smtp.example.com", 587, "STARTTLS", "user",
                "cipher", "1", "no-reply@example.com", "vane", enabled, isDefault, 500, 10000);
    }

    @Test
    void firstProviderBecomesDefaultAndPasswordIsMasked() {
        when(providers.findByCode("smtp")).thenReturn(Optional.empty());
        when(providers.count()).thenReturn(0L);
        when(cipher.configured()).thenReturn(true);
        when(cipher.encrypt(anyString(), anyString())).thenReturn("cipher");
        when(cipher.activeVersion()).thenReturn("1");

        MailProviderView created = service.create(command(true, false));

        assertThat(created.isDefault()).isTrue();
        assertThat(created.password()).isEqualTo("***");
        verify(providers).insert(any(MailProviderRow.class));
    }

    @Test
    void duplicateCodeIsRejected() {
        when(providers.findByCode("smtp")).thenReturn(Optional.of(row(true, false)));

        assertThatThrownBy(() -> service.create(command(true, false)))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).errorCode())
                .isEqualTo(CommonErrors.MAIL_PROVIDER_CODE_TAKEN);
    }

    @Test
    void disablingDefaultProviderIsRejected() {
        when(providers.findByCode("smtp")).thenReturn(Optional.of(row(true, true)));

        assertThatThrownBy(() -> service.update("smtp", command(false, true)))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).errorCode())
                .isEqualTo(CommonErrors.MAIL_PROVIDER_IN_USE);
    }

    @Test
    void updatingWithoutPasswordKeepsExistingCipher() {
        when(providers.findByCode("smtp")).thenReturn(Optional.of(row(true, true)));

        service.update("smtp", new MailProviderView.Command("smtp", "SMTP2", "smtp.example.com", 587,
                "STARTTLS", "user", null, "no-reply@example.com", "vane", true, true, 500, 10000));

        verify(providers).update(any(MailProviderRow.class));
        verify(cipher, never()).encrypt(anyString(), anyString());
    }

    @Test
    void deletingDefaultProviderIsRejected() {
        when(providers.findByCode("smtp")).thenReturn(Optional.of(row(true, true)));

        assertThatThrownBy(() -> service.delete("smtp"))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).errorCode())
                .isEqualTo(CommonErrors.MAIL_PROVIDER_IN_USE);
    }

    @Test
    void invalidProviderParametersAreRejected() {
        assertThatThrownBy(() -> service.create(new MailProviderView.Command("smtp", "SMTP",
                "smtp.example.com", 0, "STARTTLS", null, null, "no-reply@example.com", null, true,
                false, 500, 10000)))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.create(new MailProviderView.Command("smtp", "SMTP",
                "smtp.example.com", 587, "NONE", null, null, "not-an-email", null, true, false, 500, 10000)))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void listNeverExposesCipher() {
        when(providers.findAll()).thenReturn(List.of(row(true, true)));

        assertThat(service.list()).singleElement()
                .satisfies(view -> {
                    assertThat(view.password()).isEqualTo("***");
                    assertThat(view.code()).isEqualTo("smtp");
                });
    }
}
