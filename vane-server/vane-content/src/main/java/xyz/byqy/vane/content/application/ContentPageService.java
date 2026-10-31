package xyz.byqy.vane.content.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import xyz.byqy.vane.common.error.ApiException;
import xyz.byqy.vane.common.error.CommonErrors;
import xyz.byqy.vane.content.domain.CmsPage;
import xyz.byqy.vane.content.domain.CmsPageRepository;

/** 内容页（ADM-08）：slug 唯一，正文按语言存 cms_page_i18n。 */
@Service
public class ContentPageService {

    private final CmsPageRepository pages;
    private final JdbcTemplate jdbc;

    public ContentPageService(CmsPageRepository pages, JdbcTemplate jdbc) {
        this.pages = pages;
        this.jdbc = jdbc;
    }

    @Transactional
    public PageView create(String slug, UUID coverFileId, List<PageTranslation> translations, UUID authorId) {
        requireTranslations(translations);
        if (pages.existsBySlugAndDeletedAtIsNull(slug)) {
            throw new ApiException(CommonErrors.CONTENT_SLUG_TAKEN);
        }
        CmsPage page = CmsPage.create(UUID.randomUUID(), slug, coverFileId, authorId);
        pages.saveAndFlush(page);
        replaceTranslations(page.getId(), translations);
        return view(page, translations);
    }

    @Transactional
    public PageView update(UUID id, UUID coverFileId, List<PageTranslation> translations) {
        CmsPage page = require(id);
        requireTranslations(translations);
        page.update(coverFileId);
        pages.save(page);
        replaceTranslations(id, translations);
        return view(page, translations);
    }

    @Transactional
    public PageView publish(UUID id) {
        CmsPage page = require(id);
        page.publish(Instant.now());
        pages.save(page);
        return view(page, translations(id));
    }

    @Transactional
    public void delete(UUID id) {
        CmsPage page = require(id);
        page.softDelete();
        pages.save(page);
    }

    @Transactional(readOnly = true)
    public PageResult<PageView> adminPage(int pageNumber, int pageSize) {
        int size = Math.min(Math.max(pageSize, 1), 100);
        int number = Math.max(pageNumber, 0);
        Page<CmsPage> page = pages.findByDeletedAtIsNull(PageRequest.of(number, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        return new PageResult<>(page.getContent().stream().map(p -> view(p, translations(p.getId()))).toList(),
                new PageResult.PageMeta(number, size, page.getTotalElements()));
    }

    /** 门户按 slug 取已发布内容，按语言回退。 */
    @Transactional(readOnly = true)
    public PublicPageView publicBySlug(String slug, String locale) {
        CmsPage page = pages.findBySlugAndStatusAndDeletedAtIsNull(slug, "PUBLISHED")
                .orElseThrow(() -> new ApiException(CommonErrors.CONTENT_NOT_FOUND));
        List<PublicPageView> rows = jdbc.query("""
                select coalesce(ti.title, tz.title, tf.title) as title,
                       coalesce(ti.summary, tz.summary, tf.summary) as summary,
                       coalesce(ti.body, tz.body, tf.body) as body
                from (select 1) dummy
                left join cms_page_i18n ti on ti.page_id = ? and ti.locale = ?
                left join cms_page_i18n tz on tz.page_id = ? and tz.locale = 'zh-CN'
                left join lateral (
                    select title, summary, body from cms_page_i18n where page_id = ? order by locale limit 1
                ) tf on true
                """, (rs, rowNum) -> new PublicPageView(page.getId(), page.getSlug(), rs.getString("title"),
                rs.getString("summary"), rs.getString("body"), page.getCoverFileId(), page.getPublishedAt()),
                page.getId(), locale, page.getId(), page.getId());
        return rows.isEmpty()
                ? new PublicPageView(page.getId(), page.getSlug(), null, null, null, page.getCoverFileId(), page.getPublishedAt())
                : rows.get(0);
    }

    private PageView view(CmsPage page, List<PageTranslation> translations) {
        return new PageView(page.getId(), page.getSlug(), page.getStatus(), page.getCoverFileId(),
                page.getPublishedAt(), translations);
    }

    private List<PageTranslation> translations(UUID pageId) {
        return jdbc.query("select locale, title, summary, body from cms_page_i18n where page_id = ? order by locale",
                (rs, rowNum) -> new PageTranslation(rs.getString("locale"), rs.getString("title"),
                        rs.getString("summary"), rs.getString("body")), pageId);
    }

    private void replaceTranslations(UUID pageId, List<PageTranslation> translations) {
        jdbc.update("delete from cms_page_i18n where page_id = ?", pageId);
        for (PageTranslation translation : translations) {
            jdbc.update("insert into cms_page_i18n (page_id, locale, title, summary, body) values (?, ?, ?, ?, ?)",
                    pageId, translation.locale(), translation.title(), translation.summary(), translation.body());
        }
    }

    private void requireTranslations(List<PageTranslation> translations) {
        if (translations == null || translations.isEmpty()) {
            throw new ApiException(CommonErrors.VALIDATION_FAILED);
        }
        for (PageTranslation translation : translations) {
            if (translation.locale() == null || translation.locale().isBlank()
                    || translation.title() == null || translation.title().isBlank()) {
                throw new ApiException(CommonErrors.VALIDATION_FAILED);
            }
        }
    }

    private CmsPage require(UUID id) {
        return pages.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ApiException(CommonErrors.CONTENT_NOT_FOUND));
    }
}
