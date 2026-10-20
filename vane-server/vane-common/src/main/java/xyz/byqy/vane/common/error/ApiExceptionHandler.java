package xyz.byqy.vane.common.error;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import xyz.byqy.vane.common.problem.ProblemDetailFactory;

/**
 * 全局异常 → Problem Details 映射。
 * 服务器错误只回 traceId，不泄露堆栈、SQL 或内部类名（03 §2）。
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    private final ProblemDetailFactory problems;

    public ApiExceptionHandler(ProblemDetailFactory problems) {
        this.problems = problems;
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ProblemDetail> handleApi(ApiException ex, HttpServletRequest request) {
        return problems.build(ex.errorCode(), ex.getMessage(), ex.detailArgs(), ex.fieldErrors(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleBeanValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<FieldErrorItem> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(this::toItem)
                .toList();
        return problems.build(CommonErrors.VALIDATION_FAILED, null, null, errors, request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> handleConstraint(ConstraintViolationException ex, HttpServletRequest request) {
        List<FieldErrorItem> errors = ex.getConstraintViolations().stream()
                .map(v -> new FieldErrorItem("/" + String.valueOf(v.getPropertyPath()).replace('.', '/'), CommonErrors.VALIDATION_INVALID.code()))
                .toList();
        return problems.build(CommonErrors.VALIDATION_FAILED, null, null, errors, request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> handleUnreadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        return problems.build(CommonErrors.REQUEST_MALFORMED, null, null, null, request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ProblemDetail> handleNoResource(NoResourceFoundException ex, HttpServletRequest request) {
        return problems.build(CommonErrors.REQUEST_NOT_FOUND, null, null, null, request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ProblemDetail> handleMethod(HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        return problems.build(CommonErrors.REQUEST_METHOD_NOT_ALLOWED, null, null, null, request);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ProblemDetail> handleMediaType(HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {
        return problems.build(CommonErrors.REQUEST_UNSUPPORTED_MEDIA_TYPE, null, null, null, request);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ProblemDetail> handleUploadSize(MaxUploadSizeExceededException ex, HttpServletRequest request) {
        return problems.build(CommonErrors.FILE_TOO_LARGE, null, null, null, request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception", ex);
        return problems.build(CommonErrors.SYS_INTERNAL_ERROR, null, null, null, request);
    }

    private FieldErrorItem toItem(FieldError error) {
        String pointer = "/" + error.getField().replace('.', '/');
        return new FieldErrorItem(pointer, CommonErrors.VALIDATION_INVALID.code());
    }
}
