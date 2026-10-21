package xyz.byqy.vane.audit.application;

import java.io.IOException;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import xyz.byqy.vane.audit.domain.SysOperLogRepository;

/** 响应完成后回填操作日志的 HTTP 状态（ADR-023 §9）；失败不影响响应。 */
@Component
public class AuditHttpStatusFilter extends OncePerRequestFilter {

    public static final String LOG_ID_ATTRIBUTE = "xyz.byqy.vane.audit.logId";

    private static final Logger log = LoggerFactory.getLogger(AuditHttpStatusFilter.class);

    private final SysOperLogRepository logs;

    public AuditHttpStatusFilter(SysOperLogRepository logs) {
        this.logs = logs;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            filterChain.doFilter(request, response);
        } finally {
            Object attribute = request.getAttribute(LOG_ID_ATTRIBUTE);
            if (attribute instanceof UUID logId) {
                try {
                    logs.updateHttpStatus(logId, (short) response.getStatus());
                } catch (RuntimeException ex) {
                    log.warn("Failed to backfill audit http status for {}", logId, ex);
                }
            }
        }
    }
}
