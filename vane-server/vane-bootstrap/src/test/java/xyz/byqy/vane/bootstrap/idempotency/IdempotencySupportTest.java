package xyz.byqy.vane.bootstrap.idempotency;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

/** 幂等适用范围与指纹算法的单元约束。 */
class IdempotencySupportTest {

    private MockHttpServletRequest post(String uri, String contentType, String key) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", uri);
        request.setContentType(contentType);
        if (key != null) {
            request.addHeader(IdempotencySupport.HEADER, key);
        }
        request.setContent("{}".getBytes(StandardCharsets.UTF_8));
        return request;
    }

    @Test
    void eligibleWhenPostJsonWithKey() {
        assertThat(IdempotencySupport.isEligible(post("/api/v1/admin/notices", "application/json", "idem-1234")))
                .isTrue();
    }

    @Test
    void notEligibleWithoutKey() {
        assertThat(IdempotencySupport.isEligible(post("/api/v1/admin/notices", "application/json", null))).isFalse();
    }

    @Test
    void notEligibleForAuthPaths() {
        assertThat(IdempotencySupport.isEligible(post("/api/v1/auth/login", "application/json", "idem-1234"))).isFalse();
    }

    @Test
    void notEligibleForMultipart() {
        assertThat(IdempotencySupport.isEligible(post("/api/v1/files", "multipart/form-data", "idem-1234"))).isFalse();
    }

    @Test
    void notEligibleForNonPost() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/admin/notices");
        request.setContentType("application/json");
        request.addHeader(IdempotencySupport.HEADER, "idem-1234");
        assertThat(IdempotencySupport.isEligible(request)).isFalse();
    }

    @Test
    void keyFormatIsValidated() {
        assertThat(IdempotencySupport.isValidKey("idem-12345678")).isTrue();
        assertThat(IdempotencySupport.isValidKey("short")).isFalse();
        assertThat(IdempotencySupport.isValidKey("idem key with space")).isFalse();
        assertThat(IdempotencySupport.isValidKey(null)).isFalse();
    }

    @Test
    void fingerprintIsStableAndBodySensitive() {
        byte[] body = "{\"a\":1}".getBytes(StandardCharsets.UTF_8);
        String first = IdempotencySupport.fingerprint("POST", "/api/v1/admin/notices", "admin", body);
        String same = IdempotencySupport.fingerprint("POST", "/api/v1/admin/notices", "admin", body);
        String other = IdempotencySupport.fingerprint("POST", "/api/v1/admin/notices", "admin",
                "{\"a\":2}".getBytes(StandardCharsets.UTF_8));
        String otherActor = IdempotencySupport.fingerprint("POST", "/api/v1/admin/notices", "agent", body);

        assertThat(first).isEqualTo(same);
        assertThat(first).isNotEqualTo(other);
        assertThat(first).isNotEqualTo(otherActor);
    }
}
