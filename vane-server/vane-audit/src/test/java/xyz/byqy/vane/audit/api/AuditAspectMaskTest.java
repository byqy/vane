package xyz.byqy.vane.audit.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AuditAspectMaskTest {

    @Test
    void masksPasswordAndTokenFields() {
        String json = "{\"username\":\"bob\",\"password\":\"Secret123\",\"refreshToken\":\"abc\"}";

        String masked = AuditAspect.maskSensitive(json);

        assertThat(masked).contains("\"username\":\"bob\"");
        assertThat(masked).doesNotContain("Secret123");
        assertThat(masked).doesNotContain("abc");
        assertThat(masked).contains("\"***\"");
    }

    @Test
    void keepsNonSensitiveFieldsIntact() {
        String json = "[{\"nickname\":\"Bobby\",\"status\":\"DISABLED\"}]";

        assertThat(AuditAspect.maskSensitive(json)).isEqualTo(json);
    }
}
