package xyz.byqy.vane.audit.api;

import java.util.Arrays;
import java.util.UUID;
import java.util.regex.Pattern;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import tools.jackson.databind.ObjectMapper;
import xyz.byqy.vane.audit.domain.SysOperLog;
import xyz.byqy.vane.audit.domain.SysOperLogRepository;
import xyz.byqy.vane.common.error.ApiException;

/**
 * 操作日志切面（06 §9）：记录操作人、动作、结果、耗时、IP 与 traceId。
 * 审计写入失败不得影响主流程，因此整体 try/catch 且只告警。
 */
@Aspect
@Component
public class AuditAspect {

    private static final Logger log = LoggerFactory.getLogger(AuditAspect.class);
    private static final Pattern SENSITIVE = Pattern.compile("(?i)(\"(?:[a-z0-9_]*?(?:password|secret|token)[a-z0-9_]*)\"\\s*:\\s*)\"[^\"]*\"");

    private final SysOperLogRepository logs;
    private final ObjectMapper objectMapper;

    public AuditAspect(SysOperLogRepository logs, ObjectMapper objectMapper) {
        this.logs = logs;
        this.objectMapper = objectMapper;
    }

    @Around("@annotation(audit)")
    public Object around(ProceedingJoinPoint joinPoint, Audit audit) throws Throwable {
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
            long durationMs = (System.nanoTime() - startedAt) / 1_000_000;
            record(joinPoint, audit, result, errorCode, durationMs);
        }
    }

    private void record(ProceedingJoinPoint joinPoint, Audit audit, String result, String errorCode, long durationMs) {
        try {
            HttpServletRequest request = currentRequest();
            Object principal = currentPrincipal();
            UUID userId = null;
            String username = null;
            if (principal != null) {
                try {
                    userId = (UUID) principal.getClass().getMethod("id").invoke(principal);
                    username = (String) principal.getClass().getMethod("username").invoke(principal);
                } catch (ReflectiveOperationException ignored) {
                    // 非预期主体类型，仅记录空操作人
                }
            }
            logs.save(new SysOperLog(UUID.randomUUID(), userId, username, audit.module(), audit.action(),
                    request == null ? null : request.getMethod(),
                    request == null ? null : request.getRequestURI(),
                    sanitizedParams(joinPoint.getArgs()),
                    result, errorCode, (int) durationMs,
                    request == null ? null : clientIp(request),
                    MDC.get("traceId"), java.time.Instant.now()));
        } catch (Exception ex) {
            log.warn("Failed to write operation log for {}.{}", audit.module(), audit.action(), ex);
        }
    }

    /** 脱敏密码、令牌等敏感字段（保留键名，值替换为 ***）。 */
    static String maskSensitive(String json) {
        return json == null ? null : SENSITIVE.matcher(json).replaceAll("\"***\"");
    }

    private String sanitizedParams(Object[] args) {
        try {
            Object[] serializable = Arrays.stream(args)
                    .filter(arg -> arg != null && !(arg instanceof jakarta.servlet.ServletRequest)
                            && !(arg instanceof jakarta.servlet.ServletResponse))
                    .toArray();
            String json = objectMapper.writeValueAsString(serializable);
            return SENSITIVE.matcher(json).replaceAll("$1\"***\"");
        } catch (Exception ex) {
            return null;
        }
    }

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }

    private Object currentPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null ? null : authentication.getPrincipal();
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}


