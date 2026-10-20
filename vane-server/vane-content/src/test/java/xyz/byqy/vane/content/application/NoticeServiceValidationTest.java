package xyz.byqy.vane.content.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import xyz.byqy.vane.common.error.ApiException;
import xyz.byqy.vane.common.error.CommonErrors;
import xyz.byqy.vane.content.domain.CmsNoticeRepository;

class NoticeServiceValidationTest {

    private final CmsNoticeRepository notices = mock(CmsNoticeRepository.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final NoticeService service = new NoticeService(notices, jdbc);

    @Test
    void rejectsEmptyTranslations() {
        assertThatThrownBy(() -> service.create("INFO", false, null, null, List.of(), UUID.randomUUID()))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).errorCode())
                .isEqualTo(CommonErrors.VALIDATION_FAILED);
    }

    @Test
    void rejectsTranslationWithoutLocale() {
        List<NoticeTranslation> translations = List.of(new NoticeTranslation(" ", "标题", "正文"));

        assertThatThrownBy(() -> service.create("INFO", false, null, null, translations, UUID.randomUUID()))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).errorCode())
                .isEqualTo(CommonErrors.VALIDATION_FAILED);
    }
}
