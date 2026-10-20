package xyz.byqy.vane.common.web;

import java.io.IOException;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 生成或透传 X-Request-Id，写入 MDC 与请求属性，并在响应头回写。
 * 上游网关已提供 traceId 时透传，避免链路断裂。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String incoming = request.getHeader(TraceId.HEADER);
        String traceId = (incoming == null || incoming.isBlank()) ? TraceId.generate() : incoming.trim();
        MDC.put(TraceId.MDC_KEY, traceId);
        request.setAttribute(TraceId.REQUEST_ATTR, traceId);
        response.setHeader(TraceId.HEADER, traceId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(TraceId.MDC_KEY);
        }
    }
}
