package xyz.byqy.vane.api.agent;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import xyz.byqy.vane.common.error.ApiException;
import xyz.byqy.vane.common.error.CommonErrors;
import xyz.byqy.vane.iam.api.security.AuthenticatedUser;
import xyz.byqy.vane.system.application.ConfirmationStore;
import xyz.byqy.vane.system.application.ConfirmationToken;

/**
 * 高危操作二次确认（ADR-030 P2）。
 *
 * <p>服务账号执行删除类操作前，必须先申请确认令牌，再在请求头 {@code X-Vane-Confirm} 回传。
 * 人工账号不受此约束，避免影响后台界面。</p>
 */
@RestController
@RequestMapping("/api/v1/agent")
public class AgentConfirmationController {

    private static final Duration TTL = Duration.ofMinutes(5);

    private final ConfirmationStore store;

    public AgentConfirmationController(ConfirmationStore store) {
        this.store = store;
    }

    @PostMapping("/confirmations")
    @PreAuthorize("isAuthenticated()")
    public Confirmation create(@RequestBody ConfirmationRequest request,
                               @AuthenticationPrincipal AuthenticatedUser user) {
        if (request == null || request.method() == null || request.path() == null) {
            throw new ApiException(CommonErrors.REQUEST_MALFORMED, "需要 method 与 path");
        }
        String method = request.method().toUpperCase(java.util.Locale.ROOT);
        if (!"DELETE".equals(method)) {
            throw new ApiException(CommonErrors.REQUEST_MALFORMED, "当前只为 DELETE 签发确认令牌");
        }
        if (!request.path().startsWith("/api/v1/")) {
            throw new ApiException(CommonErrors.REQUEST_MALFORMED, "path 必须以 /api/v1/ 开头");
        }
        ConfirmationToken token = new ConfirmationToken(UUID.randomUUID().toString(),
                user == null ? "anonymous" : user.username(), method, request.path());
        store.save(token, TTL);
        return new Confirmation(token.id(), token.method(), token.path(), Instant.now().plus(TTL));
    }

    /**
     * 确认申请。
     *
     * @param method 待执行的 HTTP 方法（当前仅 DELETE）
     * @param path   待执行的具体路径，需与实际请求一致
     */
    public record ConfirmationRequest(String method, String path) {
    }

    /**
     * 确认令牌。
     *
     * @param token     回传给 {@code X-Vane-Confirm} 的值
     * @param method    绑定的方法
     * @param path      绑定的路径
     * @param expiresAt 过期时间
     */
    public record Confirmation(String token, String method, String path, Instant expiresAt) {
    }
}
