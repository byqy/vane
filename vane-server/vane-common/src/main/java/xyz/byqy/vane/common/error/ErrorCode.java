package xyz.byqy.vane.common.error;

import java.util.Locale;

import org.springframework.http.HttpStatusCode;

/**
 * 业务错误码。编码格式为「域.资源.原因」，大写、点分隔，例如 IAM.USER.EMAIL_TAKEN。
 */
public interface ErrorCode {

    String code();

    HttpStatusCode status();

    ProblemType type();

    /** 错误恢复指令：客户端收到错误后下一步该做什么（ADR-030 语义层）。 */
    ErrorAction action();

    /** 客户端是否可原样重试（仅瞬时失败为真）。 */
    default boolean retryable() {
        return action().retryable();
    }

    /** 面向用户的 detail 文案的 i18n key。 */
    default String detailKey() {
        return "error." + code().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
