package xyz.byqy.vane.content.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import xyz.byqy.vane.common.error.ApiException;
import xyz.byqy.vane.common.error.CommonErrors;
import xyz.byqy.vane.content.domain.CmsPageRepository;

class ContentPageServiceTest {

    private final CmsPageRepository pages = mock(CmsPageRepository.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final ContentPageService service = new ContentPageService(pages, jdbc);

    @Test
    void rejectsEmptyTranslations() {
        assertThatThrownBy(() -> service.create("about", null, List.of(), UUID.randomUUID()))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).errorCode())
                .isEqualTo(CommonErrors.VALIDATION_FAILED);
    }

    @Test
    void rejectsDuplicateSlug() {
        when(pages.existsBySlugAndDeletedAtIsNull("about")).thenReturn(true);
        List<PageTranslation> translations = List.of(new PageTranslation("zh-CN", "关于", null, "正文"));

        assertThatThrownBy(() -> service.create("about", null, translations, UUID.randomUUID()))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).errorCode())
                .isEqualTo(CommonErrors.CONTENT_SLUG_TAKEN);
    }
}
