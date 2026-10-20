package xyz.byqy.vane.common.problem;

import java.io.IOException;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import tools.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import xyz.byqy.vane.common.error.ErrorCode;

/**
 * 在 MVC 之外（如 Spring Security 过滤器链）写出 Problem Details 响应。
 * 用于 401 / 403 这类尚未进入 DispatcherServlet 的错误。
 */
@Component
public class ProblemDetailWriter {

    private final ProblemDetailFactory factory;
    private final ObjectMapper objectMapper;

    public ProblemDetailWriter(ProblemDetailFactory factory, ObjectMapper objectMapper) {
        this.factory = factory;
        this.objectMapper = objectMapper;
    }

    public void write(HttpServletRequest request, HttpServletResponse response, ErrorCode errorCode) throws IOException {
        ResponseEntity<ProblemDetail> entity = factory.build(errorCode, null, null, null, request);
        response.setStatus(entity.getStatusCode().value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        entity.getHeaders().forEach((name, values) -> {
            if (HttpHeaders.CONTENT_TYPE.equalsIgnoreCase(name) || HttpHeaders.CONTENT_LENGTH.equalsIgnoreCase(name)) {
                return;
            }
            for (String value : values) {
                response.addHeader(name, value);
            }
        });
        objectMapper.writeValue(response.getOutputStream(), entity.getBody());
    }

    /** 预留给需要自定义 errors[] 的场景。 */
    public void write(HttpServletRequest request, HttpServletResponse response, ErrorCode errorCode,
                      List<?> errors) throws IOException {
        ResponseEntity<ProblemDetail> entity = factory.build(errorCode, null, null, null, request);
        response.setStatus(entity.getStatusCode().value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        entity.getHeaders().forEach((name, values) -> {
            if (HttpHeaders.CONTENT_TYPE.equalsIgnoreCase(name) || HttpHeaders.CONTENT_LENGTH.equalsIgnoreCase(name)) {
                return;
            }
            for (String value : values) {
                response.addHeader(name, value);
            }
        });
        ProblemDetail body = entity.getBody();
        if (body != null && errors != null && !errors.isEmpty()) {
            body.setProperty("errors", errors);
        }
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
