package xyz.byqy.vane.iam.api.security;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.servlet.http.HttpServletRequest;
import xyz.byqy.vane.common.error.CommonErrors;
import xyz.byqy.vane.common.problem.ProblemDetailFactory;

/**
 * 方法级安全抛出的拒绝异常必须映射为 403/401，
 * 否则会被通用异常处理器兜成 500（实测踩到）。
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SecurityExceptionHandler {

    private final ProblemDetailFactory problems;

    public SecurityExceptionHandler(ProblemDetailFactory problems) {
        this.problems = problems;
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return problems.build(CommonErrors.AUTH_FORBIDDEN, null, null, null, request);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ProblemDetail> handleAuthentication(AuthenticationException ex, HttpServletRequest request) {
        return problems.build(CommonErrors.AUTH_TOKEN_MISSING, null, null, null, request);
    }
}
