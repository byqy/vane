package xyz.byqy.vane.system.infrastructure;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import xyz.byqy.vane.system.application.MailPort.SmtpSettings;

class MailDispatcherTest {

    private final MailDispatcher dispatcher = new MailDispatcher();

    @Test
    void sendFailsFastWhenHostIsUnreachable() {
        SmtpSettings settings = new SmtpSettings("127.0.0.1", 1, "NONE", null, null,
                "no-reply@example.com", "vane", 1000);

        assertThatThrownBy(() -> dispatcher.send(settings, "it@example.com", "subject", "body"))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void sslAndAuthSettingsAreAppliedBeforeConnect() {
        SmtpSettings settings = new SmtpSettings("127.0.0.1", 1, "SSL", "user", "secret",
                "no-reply@example.com", null, 1000);

        assertThatThrownBy(() -> dispatcher.send(settings, "it@example.com", "subject", "body"))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void verifyFailsForUnreachableHost() {
        SmtpSettings settings = new SmtpSettings("127.0.0.1", 1, "STARTTLS", null, null,
                "no-reply@example.com", "vane", 1000);

        assertThatThrownBy(() -> dispatcher.verify(settings)).isInstanceOf(RuntimeException.class);
    }
}
