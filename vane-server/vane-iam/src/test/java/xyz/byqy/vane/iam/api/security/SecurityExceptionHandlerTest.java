package xyz.byqy.vane.iam.api.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;

import xyz.byqy.vane.common.error.CommonErrors;
import xyz.byqy.vane.common.i18n.MessageResolver;
import xyz.byqy.vane.common.problem.ProblemDetailFactory;

/** 回归测试：方法级安全拒绝必须返回 403，而不是被兜成 500。 */
class SecurityExceptionHandlerTest {

    private SecurityExceptionHandler handler() {
        LocaleContextHolder.setLocale(Locale.SIMPLIFIED_CHINESE);
        StaticMessageSource source = new StaticMessageSource();
        source.addMessage("error.title.forbidden", Locale.SIMPLIFIED_CHINESE, "没有权限");
        source.addMessage("error.auth.forbidden", Locale.SIMPLIFIED_CHINESE, "没有访问该操作的权限");
        return new SecurityExceptionHandler(new ProblemDetailFactory(new MessageResolver(source)));
    }

    @Test
    void accessDeniedMapsTo403() {
        ResponseEntity<ProblemDetail> response = handler()
                .handleAccessDenied(new AccessDeniedException("denied"), null);

        assertThat(response.getStatusCode().value()).isEqualTo(403);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getProperties()).containsEntry("code", CommonErrors.AUTH_FORBIDDEN.code());
        assertThat(response.getBody().getType()).hasToString("/problems/forbidden");
    }
}
