package xyz.byqy.vane.bootstrap.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import xyz.byqy.vane.common.error.ApiException;
import xyz.byqy.vane.system.application.IdempotencyRecord;
import xyz.byqy.vane.system.application.IdempotencyStore;

/** 幂等判定的分支覆盖：首次、重放、异体冲突、处理中、非法键。 */
class IdempotencyInterceptorTest {

    /** 内存实现，便于控制状态。 */
    static final class FakeStore implements IdempotencyStore {
        private final Map<String, IdempotencyRecord> records = new HashMap<>();

        @Override
        public Optional<IdempotencyRecord> find(String scopeKey) {
            return Optional.ofNullable(records.get(scopeKey));
        }

        @Override
        public boolean tryStart(String scopeKey, IdempotencyRecord record, Duration ttl) {
            return records.putIfAbsent(scopeKey, record) == null;
        }

        @Override
        public void complete(String scopeKey, IdempotencyRecord record, Duration ttl) {
            records.put(scopeKey, record);
        }

        @Override
        public void release(String scopeKey) {
            records.remove(scopeKey);
        }
    }

    private final FakeStore store = new FakeStore();
    private final IdempotencyInterceptor interceptor = new IdempotencyInterceptor(store, Duration.ofHours(24));

    @BeforeEach
    void setUpAuthentication() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin", "n/a", java.util.List.of()));
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    private CachedBodyHttpServletRequest request(String key, String body) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/admin/notices");
        request.setContentType("application/json");
        if (key != null) {
            request.addHeader(IdempotencySupport.HEADER, key);
        }
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        return new CachedBodyHttpServletRequest(request, body.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void firstRequestAcquiresKeyAndProceeds() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(request("idem-0001", "{\"a\":1}"), response, new Object())).isTrue();
        assertThat(store.find("admin:idem-0001")).isPresent();
    }

    @Test
    void replayReturnsFirstResponseAndIsFlagged() throws Exception {
        MockHttpServletResponse first = new MockHttpServletResponse();
        interceptor.preHandle(request("idem-0002", "{\"a\":1}"), first, new Object());
        store.complete("admin:idem-0002", IdempotencyRecord.done(
                IdempotencySupport.fingerprint("POST", "/api/v1/admin/notices", "admin",
                        "{\"a\":1}".getBytes(StandardCharsets.UTF_8)),
                201, "application/json", "/api/v1/admin/notices/1", "{\"id\":\"1\"}"), Duration.ofHours(24));

        MockHttpServletResponse response = new MockHttpServletResponse();
        assertThat(interceptor.preHandle(request("idem-0002", "{\"a\":1}"), response, new Object())).isFalse();
        assertThat(response.getStatus()).isEqualTo(201);
        assertThat(response.getHeader(IdempotencySupport.REPLAY_HEADER)).isEqualTo("true");
        assertThat(response.getContentAsString()).isEqualTo("{\"id\":\"1\"}");
    }

    @Test
    void sameKeyDifferentBodyConflicts() throws Exception {
        interceptor.preHandle(request("idem-0003", "{\"a\":1}"), new MockHttpServletResponse(), new Object());

        assertThatThrownBy(() -> interceptor.preHandle(request("idem-0003", "{\"a\":2}"),
                new MockHttpServletResponse(), new Object()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Idempotency-Key");
    }

    @Test
    void concurrentRequestGetsConflictWithRetryAfter() throws Exception {
        // 首次占用键
        assertThat(interceptor.preHandle(request("idem-0004", "{\"a\":1}"),
                new MockHttpServletResponse(), new Object())).isTrue();

        // 键被占用后再来一次：处于处理中
        MockHttpServletResponse response = new MockHttpServletResponse();
        assertThatThrownBy(() -> interceptor.preHandle(request("idem-0004", "{\"a\":1}"), response, new Object()))
                .isInstanceOf(ApiException.class);
        assertThat(response.getHeader("Retry-After")).isEqualTo("1");
    }

    @Test
    void invalidKeyIsRejected() throws Exception {
        assertThatThrownBy(() -> interceptor.preHandle(request("bad", "{\"a\":1}"),
                new MockHttpServletResponse(), new Object()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("8-128");
    }

    @Test
    void requestWithoutKeyIsNotIntercepted() throws Exception {
        MockHttpServletRequest plain = new MockHttpServletRequest("POST", "/api/v1/admin/notices");
        plain.setContentType("application/json");
        plain.setContent("{}".getBytes(StandardCharsets.UTF_8));

        assertThat(interceptor.preHandle(plain, new MockHttpServletResponse(), new Object())).isTrue();
        assertThat(store.find("admin:")).isEmpty();
    }
}
