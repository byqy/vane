package xyz.byqy.vane.bootstrap.idempotency;

import java.io.IOException;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.util.ContentCachingResponseWrapper;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import xyz.byqy.vane.system.application.IdempotencyRecord;
import xyz.byqy.vane.system.application.IdempotencyStore;

/**
 * 幂等执行的过滤器（ADR-030 执行层）。
 *
 * <p>职责：缓存请求体以便计算指纹、缓存响应体以便重放、在请求结束时落库结果。
 * 判定与冲突处理在 {@link IdempotencyInterceptor} 中完成。</p>
 */
public class IdempotencyFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyFilter.class);

    private final IdempotencyStore store;
    private final Duration ttl;
    private final int maxBodyBytes;

    public IdempotencyFilter(IdempotencyStore store, Duration ttl, int maxBodyBytes) {
        this.store = store;
        this.ttl = ttl;
        this.maxBodyBytes = maxBodyBytes;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!IdempotencySupport.isEligible(request)) {
            chain.doFilter(request, response);
            return;
        }
        byte[] body = request.getInputStream().readNBytes(maxBodyBytes + 1);
        if (body.length > maxBodyBytes) {
            // 请求体过大，不做幂等缓存，交由业务正常处理
            chain.doFilter(request, response);
            return;
        }
        CachedBodyHttpServletRequest cachedRequest = new CachedBodyHttpServletRequest(request, body);
        ContentCachingResponseWrapper cachedResponse = new ContentCachingResponseWrapper(response);
        boolean failed = false;
        try {
            chain.doFilter(cachedRequest, cachedResponse);
        } catch (IOException | ServletException | RuntimeException e) {
            failed = true;
            releaseQuietly(cachedRequest);
            throw e;
        } finally {
            if (!failed) {
                persist(cachedRequest, cachedResponse);
            }
            cachedResponse.copyBodyToResponse();
        }
    }

    /** 落库结果：5xx 不确定结果时释放键，允许用同一键重试。 */
    private void persist(CachedBodyHttpServletRequest request, ContentCachingResponseWrapper response) {
        String scopeKey = (String) request.getAttribute(IdempotencySupport.ATTR_SCOPE_KEY);
        if (scopeKey == null) {
            return;
        }
        String fingerprint = (String) request.getAttribute(IdempotencySupport.ATTR_FINGERPRINT);
        int status = response.getStatus();
        try {
            if (status >= 500) {
                store.release(scopeKey);
                return;
            }
            String contentType = response.getContentType();
            String location = response.getHeader("Location");
            String body = new String(response.getContentAsByteArray(), java.nio.charset.StandardCharsets.UTF_8);
            store.complete(scopeKey, IdempotencyRecord.done(fingerprint, status, contentType, location, body), ttl);
        } catch (RuntimeException e) {
            // 存储不可用不应影响主流程
            log.warn("幂等结果落库失败：key={}", scopeKey, e);
        }
    }

    private void releaseQuietly(HttpServletRequest request) {
        String scopeKey = (String) request.getAttribute(IdempotencySupport.ATTR_SCOPE_KEY);
        if (scopeKey == null) {
            return;
        }
        try {
            store.release(scopeKey);
        } catch (RuntimeException e) {
            log.warn("幂等键释放失败：key={}", scopeKey, e);
        }
    }
}
