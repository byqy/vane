package xyz.byqy.vane.bootstrap.idempotency;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import xyz.byqy.vane.system.application.IdempotencyRecord;
import xyz.byqy.vane.system.application.IdempotencyStore;

/** 过滤器行为：落库、释放与超大请求体跳过。 */
class IdempotencyFilterTest {

    static final class FakeStore implements IdempotencyStore {
        private final Map<String, IdempotencyRecord> records = new HashMap<>();
        private int released;

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
            released++;
            records.remove(scopeKey);
        }
    }

    private final FakeStore store = new FakeStore();
    private final IdempotencyFilter filter = new IdempotencyFilter(store, Duration.ofHours(24), 1024);

    private MockHttpServletRequest request(String key, String body) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/admin/notices");
        request.setContentType("application/json");
        request.addHeader(IdempotencySupport.HEADER, key);
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        return request;
    }

    @Test
    void completesRecordAfterSuccessfulResponse() throws Exception {
        MockHttpServletRequest request = request("idem-1001", "{\"a\":1}");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> {
            HttpServletResponse http = (HttpServletResponse) res;
            http.setStatus(201);
            http.setContentType("application/json");
            try {
                http.getWriter().write("{\"id\":\"1\"}");
            } catch (java.io.IOException e) {
                throw new IllegalStateException(e);
            }
        };

        filter.doFilter(request, response, chain);

        assertThat(response.getContentAsString()).isEqualTo("{\"id\":\"1\"}");
        assertThat(store.find("admin:idem-1001")).isEmpty(); // 键由拦截器占用，过滤器只负责落库
        assertThat(store.released).isZero();
    }

    @Test
    void releasesKeyOnServerError() throws Exception {
        MockHttpServletRequest request = request("idem-1002", "{\"a\":1}");
        request.setAttribute(IdempotencySupport.ATTR_SCOPE_KEY, "admin:idem-1002");
        request.setAttribute(IdempotencySupport.ATTR_FINGERPRINT, "fp");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> ((HttpServletResponse) res).setStatus(500);

        filter.doFilter(request, response, chain);

        assertThat(store.released).isEqualTo(1);
        assertThat(store.find("admin:idem-1002")).isEmpty();
    }

    @Test
    void skipsOversizedBody() throws Exception {
        StringBuilder big = new StringBuilder("{\"a\":\"");
        big.append("x".repeat(2048)).append("\"}");
        MockHttpServletRequest request = request("idem-1003", big.toString());
        MockHttpServletResponse response = new MockHttpServletResponse();
        boolean[] called = {false};
        FilterChain chain = (req, res) -> called[0] = true;

        filter.doFilter(request, response, chain);

        assertThat(called[0]).isTrue();
        assertThat(store.released).isZero();
    }
}
