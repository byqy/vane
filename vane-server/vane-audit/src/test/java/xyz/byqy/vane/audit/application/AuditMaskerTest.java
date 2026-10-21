package xyz.byqy.vane.audit.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

class AuditMaskerTest {

    private final AuditMasker masker = new AuditMasker(JsonMapper.builder().build());

    @Test
    void masksPasswordAndTokenFields() {
        String json = "{\"username\":\"bob\",\"password\":\"Secret123\",\"refreshToken\":\"abc\"}";

        String masked = masker.maskJson(json);

        assertThat(masked).contains("\"username\":\"bob\"");
        assertThat(masked).doesNotContain("Secret123");
        assertThat(masked).doesNotContain("abc");
        assertThat(masked).contains("\"***\"");
    }

    @Test
    void keepsNonSensitiveFieldsIntact() {
        String json = "[{\"nickname\":\"Bobby\",\"status\":\"DISABLED\"}]";

        assertThat(masker.maskJson(json)).isEqualTo(json);
    }

    @Test
    void masksSecretsInsideStringifiedJson() {
        String json = "{\"valueJson\":\"{\\\"smtpPassword\\\":\\\"p@ss\\\",\\\"host\\\":\\\"smtp.example.com\\\"}\"}";

        String masked = masker.maskJson(json);

        assertThat(masked).doesNotContain("p@ss");
        assertThat(masked).contains("smtp.example.com");
    }

    @Test
    void masksPhoneEmailAndIdCardValues() {
        String json = "{\"phone\":\"+8613800138000\",\"email\":\"alice@example.com\","
                + "\"idCard\":\"110101199001011234\"}";

        String masked = masker.maskJson(json);

        assertThat(masked).doesNotContain("13800138000");
        assertThat(masked).doesNotContain("alice@example.com");
        assertThat(masked).doesNotContain("110101199001011234");
    }

    @Test
    void truncatesOversizedParams() {
        String big = "x".repeat(9000);

        String masked = masker.maskParams(new Object[]{big});

        assertThat(masked).isEqualTo("{\"__truncated__\":true}");
    }

    @Test
    void truncatesUserAgent() {
        assertThat(masker.truncateUserAgent("a".repeat(600))).hasSize(512);
    }
}
