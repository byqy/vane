package xyz.byqy.vane.system.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import xyz.byqy.vane.common.error.ApiException;
import xyz.byqy.vane.common.error.CommonErrors;
import xyz.byqy.vane.system.application.MessageTemplateService.Translation;
import xyz.byqy.vane.system.domain.SysMessageTemplate;
import xyz.byqy.vane.system.domain.SysMessageTemplateRepository;

class MessageTemplateServiceTest {

    private final SysMessageTemplateRepository templates = mock(SysMessageTemplateRepository.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final MessageTemplateService service = new MessageTemplateService(templates, jdbc);

    private SysMessageTemplate template() {
        return mock(SysMessageTemplate.class);
    }

    @SuppressWarnings("unchecked")
    private void givenTranslations(List<Translation> translations) {
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(translations);
    }

    @Test
    void rendersRequestedLocaleAndSubstitutesVariables() {
        SysMessageTemplate template = template();
        when(template.getId()).thenReturn(UUID.randomUUID());
        when(template.getCode()).thenReturn("user.welcome");
        when(template.getChannel()).thenReturn("EMAIL");
        when(template.getStatus()).thenReturn("ACTIVE");
        when(templates.findByCodeAndChannelAndDeletedAtIsNull("user.welcome", "EMAIL"))
                .thenReturn(Optional.of(template));
        givenTranslations(List.of(
                new Translation("en", "Welcome to {siteName}", "Hello {username}"),
                new Translation("zh-CN", "欢迎加入 {siteName}", "你好 {username}")));

        var rendered = service.render("user.welcome", "EMAIL", "en",
                Map.of("siteName", "vane", "username", "Alice"));

        assertThat(rendered.subject()).isEqualTo("Welcome to vane");
        assertThat(rendered.body()).isEqualTo("Hello Alice");
    }

    @Test
    void fallsBackToDefaultLocale() {
        SysMessageTemplate template = template();
        when(template.getId()).thenReturn(UUID.randomUUID());
        when(template.getCode()).thenReturn("user.welcome");
        when(template.getChannel()).thenReturn("EMAIL");
        when(template.getStatus()).thenReturn("ACTIVE");
        when(templates.findByCodeAndChannelAndDeletedAtIsNull("user.welcome", "EMAIL"))
                .thenReturn(Optional.of(template));
        givenTranslations(List.of(new Translation("zh-CN", "欢迎 {username}", "你好 {username}")));

        var rendered = service.render("user.welcome", "EMAIL", "fr", Map.of("username", "Bob"));

        assertThat(rendered.locale()).isEqualTo("zh-CN");
        assertThat(rendered.subject()).isEqualTo("欢迎 Bob");
    }

    @Test
    void unknownTemplateThrowsNotFound() {
        when(templates.findByCodeAndChannelAndDeletedAtIsNull("missing", "EMAIL")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.render("missing", "EMAIL", "en", Map.of()))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).errorCode())
                .isEqualTo(CommonErrors.MAIL_TEMPLATE_NOT_FOUND);
    }

}
