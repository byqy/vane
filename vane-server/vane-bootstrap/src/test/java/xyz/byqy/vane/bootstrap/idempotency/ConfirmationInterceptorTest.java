package xyz.byqy.vane.bootstrap.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import xyz.byqy.vane.common.error.ApiException;
import xyz.byqy.vane.iam.api.security.AuthenticatedUser;
import xyz.byqy.vane.iam.application.ServiceAccountDetector;
import xyz.byqy.vane.system.application.ConfirmationStore;
import xyz.byqy.vane.system.application.ConfirmationToken;

/** 高危操作二次确认：只约束服务账号，令牌一次性且绑定「操作者 + 方法 + 路径」。 */
class ConfirmationInterceptorTest {

    static final class FakeStore implements ConfirmationStore {
        private final Map<String, ConfirmationToken> tokens = new HashMap<>();

        @Override
        public void save(ConfirmationToken token, Duration ttl) {
            tokens.put(token.id(), token);
        }

        @Override
        public Optional<ConfirmationToken> find(String id) {
            return Optional.ofNullable(tokens.get(id));
        }

        @Override
        public boolean consume(String id) {
            return tokens.remove(id) != null;
        }
    }

    private final FakeStore store = new FakeStore();
    private final UUID userId = UUID.randomUUID();
    private boolean serviceAccount = true;
    private final ConfirmationInterceptor interceptor = new ConfirmationInterceptor(store,
            new ServiceAccountDetector(null) {
                @Override
                public boolean isServiceAccount(UUID id) {
                    return serviceAccount && userId.equals(id);
                }
            });

    private static final String PATH = "/api/v1/admin/notices/6f1f0f4c-0000-0000-0000-000000000000";

    @BeforeEach
    void authenticate() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new AuthenticatedUser(userId, "agent-ops", null), null, java.util.List.of()));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private MockHttpServletRequest request(String method, String path, String token) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        if (token != null) {
            request.addHeader(ConfirmationInterceptor.HEADER, token);
        }
        return request;
    }

    private ConfirmationToken token(String path) {
        return new ConfirmationToken("tok-1", "agent-ops", "DELETE", path);
    }

    @Test
    void serviceAccountNeedsConfirmation() {
        assertThatThrownBy(() -> interceptor.preHandle(request("DELETE", PATH, null),
                new MockHttpServletResponse(), new Object()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("二次确认");
    }

    @Test
    void validTokenPassesAndIsConsumed() {
        store.save(token(PATH), Duration.ofMinutes(5));

        assertThat(interceptor.preHandle(request("DELETE", PATH, "tok-1"),
                new MockHttpServletResponse(), new Object())).isTrue();
        // 一次性：再用同一令牌应失败
        assertThatThrownBy(() -> interceptor.preHandle(request("DELETE", PATH, "tok-1"),
                new MockHttpServletResponse(), new Object()))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void tokenBindsToPathAndActor() {
        store.save(token("/api/v1/admin/users/other"), Duration.ofMinutes(5));
        assertThatThrownBy(() -> interceptor.preHandle(request("DELETE", PATH, "tok-1"),
                new MockHttpServletResponse(), new Object()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("不匹配");
    }

    @Test
    void humanAccountsAreNotGated() {
        serviceAccount = false;
        assertThat(interceptor.preHandle(request("DELETE", PATH, null),
                new MockHttpServletResponse(), new Object())).isTrue();
    }

    @Test
    void nonDeleteRequestsAreNotGated() {
        assertThat(interceptor.preHandle(request("POST", PATH, null),
                new MockHttpServletResponse(), new Object())).isTrue();
    }
}
