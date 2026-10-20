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

    /** 客户端是否可原样重试（幂等或暂时性错误）。 */
    boolean retryable();

    /** 面向用户的 detail 文案的 i18n key。 */
    default String detailKey() {
        return "error." + code().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
