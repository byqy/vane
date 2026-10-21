package xyz.byqy.vane.audit.api;

import java.lang.reflect.Method;
import java.util.UUID;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import xyz.byqy.vane.audit.application.AuditDiff;
import xyz.byqy.vane.audit.application.AuditHttpStatusFilter;
import xyz.byqy.vane.audit.application.AuditMasker;
import xyz.byqy.vane.audit.domain.SysOperLog;
import xyz.byqy.vane.audit.domain.SysOperLogRepository;
import xyz.byqy.vane.common.audit.AuditContext;
import xyz.byqy.vane.common.error.ApiException;

/**
 * 操作日志切面（ADR-023）：记录操作人、目标对象、动作、结果、耗时、IP、UA、traceId；
 * L2 方法额外记录字段级 before/after。审计写入失败不得影响主流程。
 */
@Aspect
@Component
public class AuditAspect {

    private static final Logger log = LoggerFactory.getLogger(AuditAspect.class);
    private static final SpelExpressionParser SPEL = new SpelExpressionParser();

    private final SysOperLogRepository logs;
    private final AuditMasker masker;
    private final AuditDiff diff;

    public AuditAspect(SysOperLogRepository logs, AuditMasker masker, AuditDiff diff) {
        this.logs = logs;
        this.masker = masker;
        this.diff = diff;
    }

    @Around("@annotation(audit)")
    public Object around(ProceedingJoinPoint joinPoint, Audit audit) throws Throwable {
        AuditContext context = AuditContext.begin();
        long startedAt = System.nanoTime();
        String result = "SUCCESS";
        String errorCode = null;
        try {
            return joinPoint.proceed();
        } catch (ApiException ex) {
            result = "FAIL";
            errorCode = ex.errorCode().code();
            throw ex;
        } catch (Throwable ex) {
            result = "FAIL";
            throw ex;
        } finally {
            try {
                long durationMs = (System.nanoTime() - startedAt) / 1_000_000;
                AuditDiff.Result changes = "SUCCESS".equals(result) && audit.detail()
                        ? diff.diff(context) : null;
                record(joinPoint, audit, context, result, errorCode, durationMs, changes);
            } catch (Exception ex) {
                log.warn("Failed to write operation log for {}.{}", audit.module(), audit.action(), ex);
            } finally {
                AuditContext.clear();
            }
        }
    }

    private void record(ProceedingJoinPoint joinPoint, Audit audit, AuditContext context, String result,
                        String errorCode, long durationMs, AuditDiff.Result changes) {
        try {
            HttpServletRequest request = currentRequest();
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            Object principal = authentication == null ? null : authentication.getPrincipal();
            UUID userId = invoke(principal, "id", UUID.class);
            String username = invoke(principal, "username", String.class);
            UUID deptId = invoke(principal, "deptId", UUID.class);
            Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
            Object[] args = joinPoint.getArgs();
            String targetType = blankToNull(audit.targetType());
            String targetId = resolve(audit.targetId(), method, args);
            String targetLabel = resolve(audit.targetLabel(), method, args);
            UUID logId = UUID.randomUUID();
            String params = masker.maskParams(args);
            String userAgent = request == null ? null : masker.truncateUserAgent(request.getHeader("User-Agent"));
            Integer affectedCount = context == null ? null : context.affectedCount();
            logs.save(new SysOperLog(logId, userId, username, audit.module(), audit.action(), "API",
                    targetType, targetId, targetLabel,
                    request == null ? null : request.getMethod(),
                    request == null ? null : request.getRequestURI(),
                    null, userAgent, deptId, params,
                    changes == null ? null : changes.changesJson(),
                    (short) 1, changes != null && changes.truncated(), affectedCount,
                    result, errorCode, (int) durationMs,
                    request == null ? null : clientIp(request),
                    MDC.get("traceId"), java.time.Instant.now()));
            if (request != null) {
                request.setAttribute(AuditHttpStatusFilter.LOG_ID_ATTRIBUTE, logId);
            }
        } catch (Exception ex) {
            log.warn("Failed to write operation log for {}.{}", audit.module(), audit.action(), ex);
        }
    }

    private String resolve(String expression, Method method, Object[] args) {
        if (expression == null || expression.isBlank()) {
            return null;
        }
        try {
            var context = new MethodBasedEvaluationContext(null, method, args,
                    new DefaultParameterNameDiscoverer());
            Object value = SPEL.parseExpression(expression).getValue(context);
            return value == null ? null : String.valueOf(value);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static <T> T invoke(Object target, String method, Class<T> type) {
        if (target == null) {
            return null;
        }
        try {
            Object value = target.getClass().getMethod(method).invoke(target);
            return type.isInstance(value) ? type.cast(value) : null;
        } catch (ReflectiveOperationException ex) {
            return null;
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
