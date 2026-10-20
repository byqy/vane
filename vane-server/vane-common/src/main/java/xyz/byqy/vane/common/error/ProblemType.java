package xyz.byqy.vane.common.error;

/**
 * RFC 9457 问题类型（type）的稳定分类。
 * type 是语言无关的标识，客户端按 type + code 分支，不按 title 文案匹配。
 */
public enum ProblemType {

    VALIDATION_FAILED("validation-failed", "error.title.validation"),
    BAD_REQUEST("bad-request", "error.title.badRequest"),
    AUTH_REQUIRED("auth-required", "error.title.authRequired"),
    FORBIDDEN("forbidden", "error.title.forbidden"),
    NOT_FOUND("not-found", "error.title.notFound"),
    CONFLICT("conflict", "error.title.conflict"),
    RATE_LIMITED("rate-limited", "error.title.rateLimited"),
    UNSUPPORTED_MEDIA_TYPE("unsupported-media-type", "error.title.unsupportedMediaType"),
    PAYLOAD_TOO_LARGE("payload-too-large", "error.title.payloadTooLarge"),
    PRECONDITION_FAILED("precondition-failed", "error.title.preconditionFailed"),
    METHOD_NOT_ALLOWED("method-not-allowed", "error.title.methodNotAllowed"),
    INTERNAL_ERROR("internal-error", "error.title.internal"),
    UNAVAILABLE("unavailable", "error.title.unavailable");

    private final String slug;
    private final String titleKey;

    ProblemType(String slug, String titleKey) {
        this.slug = slug;
        this.titleKey = titleKey;
    }

    public String slug() {
        return slug;
    }

    public String titleKey() {
        return titleKey;
    }

    /** 03 §2 规定使用相对 URI，避免依赖未定域名。 */
    public String uri() {
        return "/problems/" + slug;
    }
}
