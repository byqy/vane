package xyz.byqy.vane.bootstrap.idempotency;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import xyz.byqy.vane.common.error.CommonErrors;
import xyz.byqy.vane.common.error.ApiException;
import xyz.byqy.vane.system.application.IdempotencyRecord;
import xyz.byqy.vane.system.application.IdempotencyStore;

/**
 * 幂等判定拦截器（ADR-030 执行层）。
 *
 * <p>三种结果：</p>
 * <ul>
 *   <li>首次出现：占用键并放行，由 {@link IdempotencyFilter} 落库结果；</li>
 *   <li>已完成且指纹一致：重放首次响应，带 {@code Idempotent-Replayed: true}；</li>
 *   <li>指纹不一致或正在处理中：返回 409 {@code REQUEST.IDEMPOTENCY_CONFLICT}。</li>
 * </ul>
 */
public class IdempotencyInterceptor implements HandlerInterceptor {

    private final IdempotencyStore store;
    private final Duration ttl;

    public IdempotencyInterceptor(IdempotencyStore store, Duration ttl) {
        this.store = store;
        this.ttl = ttl;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        if (!IdempotencySupport.isEligible(request)) {
            // 不参与幂等的请求（无幂等键、非创建类、认证类等）直接放行
            return true;
        }
        String key = request.getHeader(IdempotencySupport.HEADER);
        if (!IdempotencySupport.isValidKey(key)) {
            throw new ApiException(CommonErrors.REQUEST_MALFORMED,
                    "Idempotency-Key 必须是 8-128 位字母、数字或 . _ : -");
        }
        String actor = actor();
        String scopeKey = actor + ":" + key;
        byte[] body = bodyOf(request);
        String fingerprint = IdempotencySupport.fingerprint(
                request.getMethod(), request.getRequestURI(), actor, body);

        Optional<IdempotencyRecord> existing = store.find(scopeKey);
        if (existing.isPresent()) {
            IdempotencyRecord record = existing.get();
            if (!fingerprint.equals(record.fingerprint())) {
                throw new ApiException(CommonErrors.REQUEST_IDEMPOTENCY_CONFLICT,
                        "同一 Idempotency-Key 已用于不同的请求内容，请换用新的键");
            }
            if (IdempotencyRecord.STATE_DONE.equals(record.state())) {
                replay(record, response);
                return false;
            }
            response.setHeader("Retry-After", "1");
            throw new ApiException(CommonErrors.REQUEST_IDEMPOTENCY_CONFLICT,
                    "同一 Idempotency-Key 的请求正在处理中，请稍后重试");
        }

        boolean acquired = store.tryStart(scopeKey, IdempotencyRecord.inFlight(fingerprint), ttl);
        if (!acquired) {
            response.setHeader("Retry-After", "1");
            throw new ApiException(CommonErrors.REQUEST_IDEMPOTENCY_CONFLICT,
                    "同一 Idempotency-Key 的请求正在处理中，请稍后重试");
        }
        request.setAttribute(IdempotencySupport.ATTR_SCOPE_KEY, scopeKey);
        request.setAttribute(IdempotencySupport.ATTR_FINGERPRINT, fingerprint);
        return true;
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler,
                           ModelAndView modelAndView) {
        // 结果由 IdempotencyFilter 统一落库
    }

    /** 重放首次响应。 */
    private void replay(IdempotencyRecord record, HttpServletResponse response) throws IOException {
        response.setStatus(record.status() == null ? 200 : record.status());
        if (record.contentType() != null) {
            response.setContentType(record.contentType());
        }
        if (record.location() != null) {
            response.setHeader("Location", record.location());
        }
        response.setHeader(IdempotencySupport.REPLAY_HEADER, "true");
        if (record.body() != null) {
            response.getOutputStream().write(record.body().getBytes(StandardCharsets.UTF_8));
        }
    }

    /** 当前操作者：登录用户名为准，未登录时用匿名标识。 */
    private String actor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return "anonymous";
        }
        String name = authentication.getName();
        return name == null || name.isBlank() ? "anonymous" : name;
    }

    private byte[] bodyOf(HttpServletRequest request) {
        if (request instanceof CachedBodyHttpServletRequest cached) {
            return cached.body();
        }
        return new byte[0];
    }
}
