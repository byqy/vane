package xyz.byqy.vane.common.problem;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

import xyz.byqy.vane.common.error.CommonErrors;
import xyz.byqy.vane.common.error.FieldErrorItem;
import xyz.byqy.vane.common.i18n.MessageResolver;

class ProblemDetailFactoryTest {

    @AfterEach
    void resetLocale() {
        LocaleContextHolder.resetLocaleContext();
    }

    private ProblemDetailFactory factory(Locale locale, String title, String detail) {
        LocaleContextHolder.setLocale(locale);
        StaticMessageSource source = new StaticMessageSource();
        source.addMessage("error.title.authRequired", locale, title);
        source.addMessage("error.auth.token.missing", locale, detail);
        return new ProblemDetailFactory(new MessageResolver(source));
    }

    @Test
    void buildsRfc9457Shape() {
        ProblemDetailFactory factory = factory(Locale.SIMPLIFIED_CHINESE, "需要登录", "缺少访问令牌");

        ResponseEntity<ProblemDetail> response = factory.build(
                CommonErrors.AUTH_TOKEN_MISSING, null, null,
                List.of(new FieldErrorItem("/email", "VALIDATION.INVALID")), null);

        assertThat(response.getStatusCode().value()).isEqualTo(401);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(response.getHeaders().getContentLanguage()).isEqualTo(Locale.SIMPLIFIED_CHINESE);

        ProblemDetail body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getType()).hasToString("/problems/auth-required");
        assertThat(body.getTitle()).isEqualTo("需要登录");
        assertThat(body.getDetail()).isEqualTo("缺少访问令牌");
        assertThat(body.getStatus()).isEqualTo(401);
        assertThat(body.getProperties()).containsEntry("code", "AUTH.TOKEN.MISSING");
        assertThat(body.getProperties()).containsKey("errors");
        // ADR-030 语义层：恢复指令与可重试标志始终存在
        assertThat(body.getProperties()).containsEntry("action", "ASK_USER");
        assertThat(body.getProperties()).containsEntry("retryable", false);
        // 首个违规字段的 JSON Pointer
        assertThat(body.getProperties()).containsEntry("field", "/email");
    }

    @Test
    void exposesActionAndRetryableForTransientFailures() {
        ProblemDetailFactory factory = factory(Locale.SIMPLIFIED_CHINESE, "服务暂时不可用", "依赖不可用");

        ResponseEntity<ProblemDetail> response = factory.build(
                CommonErrors.SYS_DEPENDENCY_UNAVAILABLE, null, null, null, null);

        ProblemDetail body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getProperties()).containsEntry("action", "RETRY");
        assertThat(body.getProperties()).containsEntry("retryable", true);
    }

    @Test
    void omitsFieldWhenNoFieldErrors() {
        ProblemDetailFactory factory = factory(Locale.SIMPLIFIED_CHINESE, "需要登录", "缺少访问令牌");

        ResponseEntity<ProblemDetail> response = factory.build(
                CommonErrors.AUTH_TOKEN_MISSING, null, null, null, null);

        ProblemDetail body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getProperties()).doesNotContainKey("field");
        assertThat(body.getProperties()).containsEntry("action", "ASK_USER");
    }

    @Test
    void missingTranslationFallsBackToKey() {
        ProblemDetailFactory factory = factory(Locale.ENGLISH, "Authentication required", "Missing access token");

        ResponseEntity<ProblemDetail> response = factory.build(
                CommonErrors.SYS_INTERNAL_ERROR, null, null, null, null);

        ProblemDetail body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getTitle()).isEqualTo("error.title.internal");
        assertThat(body.getDetail()).isEqualTo("error.sys.internal-error");
        assertThat(body.getProperties()).doesNotContainKey("errors");
    }

    @Test
    void explicitDetailWinsOverTranslation() {
        ProblemDetailFactory factory = factory(Locale.SIMPLIFIED_CHINESE, "需要登录", "缺少访问令牌");

        ResponseEntity<ProblemDetail> response = factory.build(
                CommonErrors.AUTH_TOKEN_MISSING, "自定义说明", null, null, null);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getDetail()).isEqualTo("自定义说明");
    }
}
