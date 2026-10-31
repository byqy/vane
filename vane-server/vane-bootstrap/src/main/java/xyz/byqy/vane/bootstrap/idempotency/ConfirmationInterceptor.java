package xyz.byqy.vane.bootstrap.idempotency;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import xyz.byqy.vane.common.error.ApiException;
import xyz.byqy.vane.common.error.CommonErrors;
import xyz.byqy.vane.iam.api.security.AuthenticatedUser;
import xyz.byqy.vane.iam.application.ServiceAccountDetector;
import xyz.byqy.vane.system.application.ConfirmationStore;
import xyz.byqy.vane.system.application.ConfirmationToken;

/**
 * 高危操作二次确认拦截器（ADR-030 P2）。
 *
 * <p>只约束**服务账号**（持有服务角色）的删除类请求：必须带 {@code X-Vane-Confirm}，
 * 且令牌需与「操作者 + 方法 + 具体路径」一致、未过期、一次性。
 * 人工账号不受影响，后台界面的删除行为保持不变。</p>
 */
public class ConfirmationInterceptor implements HandlerInterceptor {

    /** 客户端回传确认令牌的头。 */
    public static final String HEADER = "X-Vane-Confirm";

    private final ConfirmationStore store;
    private final ServiceAccountDetector detector;

    public ConfirmationInterceptor(ConfirmationStore store, ServiceAccountDetector detector) {
        this.store = store;
        this.detector = detector;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!requiresConfirmation(request)) {
            return true;
        }
        AuthenticatedUser user = currentUser();
        if (user == null || !detector.isServiceAccount(user.id())) {
            // 人工账号不强制（界面行为不变）
            return true;
        }
        String token = request.getHeader(HEADER);
        if (token == null || token.isBlank()) {
            throw new ApiException(CommonErrors.REQUEST_CONFIRMATION_REQUIRED,
                    "删除操作需要二次确认：先 POST /api/v1/agent/confirmations，再把 token 放进 " + HEADER);
        }
        ConfirmationToken record = store.find(token).orElseThrow(() -> new ApiException(
                CommonErrors.REQUEST_CONFIRMATION_REQUIRED, "确认令牌不存在或已过期，请重新申请"));
        boolean matches = record.actor().equals(user.username())
                && record.method().equalsIgnoreCase(request.getMethod())
                && record.path().equals(request.getRequestURI());
        if (!matches) {
            throw new ApiException(CommonErrors.REQUEST_CONFIRMATION_REQUIRED,
                    "确认令牌与本次请求不匹配（操作者 / 方法 / 路径须一致）");
        }
        if (!store.consume(token)) {
            throw new ApiException(CommonErrors.REQUEST_CONFIRMATION_REQUIRED, "确认令牌已被使用，请重新申请");
        }
        return true;
    }

    private boolean requiresConfirmation(HttpServletRequest request) {
        if (!"DELETE".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        String uri = request.getRequestURI();
        return uri != null && uri.startsWith("/api/v1/") && !uri.startsWith("/api/v1/agent/");
    }

    private AuthenticatedUser currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        return authentication.getPrincipal() instanceof AuthenticatedUser user ? user : null;
    }
}
