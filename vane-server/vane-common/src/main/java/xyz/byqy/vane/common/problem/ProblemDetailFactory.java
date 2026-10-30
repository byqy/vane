package xyz.byqy.vane.common.problem;

import java.net.URI;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import xyz.byqy.vane.common.error.ErrorCode;
import xyz.byqy.vane.common.error.FieldErrorItem;
import xyz.byqy.vane.common.i18n.MessageResolver;
import xyz.byqy.vane.common.web.TraceId;

/** 按 03 §2 构造 RFC 9457 错误响应。 */
@Component
public class ProblemDetailFactory {

    private final MessageResolver messages;

    public ProblemDetailFactory(MessageResolver messages) {
        this.messages = messages;
    }

    public ResponseEntity<ProblemDetail> build(ErrorCode errorCode, String detail, Object[] detailArgs,
                                                List<FieldErrorItem> fieldErrors, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatus(errorCode.status());
        problem.setType(URI.create(errorCode.type().uri()));
        problem.setTitle(messages.resolve(errorCode.type().titleKey()));
        problem.setDetail(resolveDetail(errorCode, detail, detailArgs));
        if (request != null) {
            problem.setInstance(URI.create(request.getRequestURI()));
        }
        problem.setProperty("code", errorCode.code());
        String traceId = TraceId.current();
        if (!traceId.isEmpty()) {
            problem.setProperty("traceId", traceId);
        }
        if (fieldErrors != null && !fieldErrors.isEmpty()) {
            problem.setProperty("errors", fieldErrors);
            // 首个违规字段的 RFC 6901 指针，便于客户端精确定位
            problem.setProperty("field", fieldErrors.get(0).pointer());
        }
        // 恢复指令与可重试标志始终存在，客户端（含 AI Agent）无需按状态码猜测
        problem.setProperty("action", errorCode.action().name());
        problem.setProperty("retryable", errorCode.retryable());
        return ResponseEntity.status(errorCode.status())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .header(HttpHeaders.CONTENT_LANGUAGE, messages.currentLocale().toLanguageTag())
                .body(problem);
    }

    private String resolveDetail(ErrorCode errorCode, String detail, Object[] detailArgs) {
        if (detail != null && !detail.isBlank()) {
            return detail;
        }
        return messages.resolve(errorCode.detailKey(), detailArgs);
    }
}
