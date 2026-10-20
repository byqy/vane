package xyz.byqy.vane.common.error;

import java.util.List;

/** 业务异常：携带错误码、可选 detail 参数与字段级错误。 */
public class ApiException extends RuntimeException {

    private final ErrorCode errorCode;
    private final List<FieldErrorItem> fieldErrors;
    private final Object[] detailArgs;

    public ApiException(ErrorCode errorCode) {
        this(errorCode, null, List.of(), new Object[0]);
    }

    public ApiException(ErrorCode errorCode, String message) {
        this(errorCode, message, List.of(), new Object[0]);
    }

    public ApiException(ErrorCode errorCode, String message, List<FieldErrorItem> fieldErrors, Object[] detailArgs) {
        super(message);
        this.errorCode = errorCode;
        this.fieldErrors = fieldErrors == null ? List.of() : List.copyOf(fieldErrors);
        this.detailArgs = detailArgs == null ? new Object[0] : detailArgs.clone();
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    public List<FieldErrorItem> fieldErrors() {
        return fieldErrors;
    }

    public Object[] detailArgs() {
        return detailArgs.clone();
    }
}
