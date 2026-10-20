package xyz.byqy.vane.content.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import xyz.byqy.vane.common.error.ApiException;
import xyz.byqy.vane.common.error.CommonErrors;
import xyz.byqy.vane.content.domain.CmsArticle;
import xyz.byqy.vane.content.domain.CmsArticleRepository;
import xyz.byqy.vane.content.domain.CmsCategoryRepository;

import jakarta.persistence.criteria.Predicate;

/**
 * 文章服务（ADM-08）：主体走 JPA，多语言正文走 cms_article_i18n，标签整体替换走 cms_article_tag。
 *
 * <p>门户查询只返回 {@code PUBLISHED} 且未删除的文章；标题/摘要/正文按请求语言回退 zh-CN，
 * 再回退任意语言；关键词用 ILIKE + pg_trgm 做中文子串匹配。</p>
 */
@Service
public class ArticleService {

    private static final int MAX_PAGE_SIZE = 100;

    private final CmsArticleRepository articles;
    private final CmsCategoryRepository categories;
    private final JdbcTemplate jdbc;

    public ArticleService(CmsArticleRepository articles, CmsCategoryRepository categories, JdbcTemplate jdbc) {
        this.articles = articles;
        this.categories = categories;
        this.jdbc = jdbc;
    }

    @Transactional
    public ArticleView create(String slug, UUID categoryId, UUID coverFileId,
                              List<ArticleTranslation> translations, UUID authorId) {
        requireTranslations(translations);
        if (articles.existsBySlugAndDeletedAtIsNull(slug)) {
            throw new ApiException(CommonErrors.CONTENT_SLUG_TAKEN);
        }
        requireCategory(categoryId);
        CmsArticle article = CmsArticle.create(UUID.randomUUID(), slug, categoryId, coverFileId, authorId);
        articles.saveAndFlush(article);
        replaceTranslations(article.getId(), translations);
        return view(article, List.of());
    }

    @Transactional
    public ArticleView update(UUID id, String slug, UUID categoryId, UUID coverFileId,
                              List<ArticleTranslation> translations) {
        CmsArticle article = require(id);
        requireTranslations(translations);
        if (articles.existsBySlugAndDeletedAtIsNull(slug) && !article.getSlug().equals(slug)) {
            throw new ApiException(CommonErrors.CONTENT_SLUG_TAKEN);
        }
        requireCategory(categoryId);
        article.update(slug, categoryId, coverFileId);
        articles.save(article);
        replaceTranslations(id, translations);
        return view(article, tags(id));
    }

    @Transactional
    public ArticleView publish(UUID id) {
        CmsArticle article = require(id);
        article.publish(Instant.now());
        articles.save(article);
        return view(article, tags(id));
    }

    @Transactional
    public ArticleView unpublish(UUID id) {
        CmsArticle article = require(id);
        article.unpublish();
        articles.save(article);
        return view(article, tags(id));
    }

    @Transactional
    public void delete(UUID id) {
        CmsArticle article = require(id);
        article.softDelete();
        articles.save(article);
    }

    @Transactional(readOnly = true)
    public List<TagView> tags(UUID articleId) {
        require(articleId);
        return articleTags(articleId);
    }

    /** 标签整体替换：先清空再写入，未知标签 → 404。 */
    @Transactional
    public List<TagView> replaceTags(UUID articleId, List<UUID> tagIds, UUID actorId) {
        CmsArticle article = require(articleId);
        List<UUID> distinct = tagIds == null ? List.of() : tagIds.stream().distinct().toList();
        for (UUID tagId : distinct) {
            requireTag(tagId);
        }
        jdbc.update("delete from cms_article_tag where article_id = ?", article.getId());
        for (UUID tagId : distinct) {
            jdbc.update("insert into cms_article_tag (article_id, tag_id, created_by) values (?, ?, ?)",
                    article.getId(), tagId, actorId);
        }
        return articleTags(articleId);
    }

    /** 后台分页：按状态、分类与关键词过滤；关键词跨全部语言匹配标题/摘要。 */
    @Transactional(readOnly = true)
    public PageResult<ArticleView> adminPage(int pageNumber, int pageSize, String status, UUID categoryId,
                                             String keyword) {
        int size = clampSize(pageSize);
        int number = Math.max(pageNumber, 0);
        List<UUID> keywordIds = keywordArticleIds(keyword);
        Specification<CmsArticle> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isNull(root.get("deletedAt")));
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (categoryId != null) {
                predicates.add(cb.equal(root.get("categoryId"), categoryId));
            }
            if (keywordIds != null) {
                predicates.add(keywordIds.isEmpty() ? cb.disjunction() : root.get("id").in(keywordIds));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        Page<CmsArticle> page = articles.findAll(spec,
                PageRequest.of(number, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        List<ArticleView> items = page.getContent().stream()
                .map(article -> view(article, articleTags(article.getId())))
                .toList();
        return new PageResult<>(items, new PageResult.PageMeta(number, size, page.getTotalElements()));
    }

    /** 门户分页：只含已发布文章，支持分类 code、标签 code 与关键词过滤。 */
    @Transactional(readOnly = true)
    public PageResult<PublicArticleView> publicPage(String locale, String categoryCode, String tagCode,
                                                    String keyword, int pageNumber, int pageSize) {
        int size = clampSize(pageSize);
        int number = Math.max(pageNumber, 0);

        StringBuilder where = new StringBuilder(" where a.status = 'PUBLISHED' and a.deleted_at is null");
        List<Object> filters = new ArrayList<>();
        if (categoryCode != null && !categoryCode.isBlank()) {
            where.append(" and c.code = ?");
            filters.add(categoryCode);
        }
        if (tagCode != null && !tagCode.isBlank()) {
            where.append("""
                     and exists (select 1 from cms_article_tag at2
                                  join cms_tag tg on tg.id = at2.tag_id
                                 where at2.article_id = a.id and tg.code = ? and tg.deleted_at is null)
                    """);
            filters.add(tagCode);
        }
        if (keyword != null && !keyword.isBlank()) {
            where.append("""
                     and exists (select 1 from cms_article_i18n s
                                 where s.article_id = a.id and s.locale in (?, 'zh-CN')
                                   and (s.title ilike ? or coalesce(s.summary, '') ilike ?))
                    """);
            filters.add(locale);
            filters.add(like(keyword));
            filters.add(like(keyword));
        }

        String select = """
                select a.id, a.slug, a.cover_file_id, a.published_at,
                       coalesce(ti.title, tz.title, tf.title) as title,
                       coalesce(ti.summary, tz.summary, tf.summary) as summary,
                       c.code as category_code,
                       coalesce(ci.name, cz.name, cf.name) as category_name
                from cms_article a
                left join cms_article_i18n ti on ti.article_id = a.id and ti.locale = ?
                left join cms_article_i18n tz on tz.article_id = a.id and tz.locale = 'zh-CN'
                left join lateral (
                    select title, summary from cms_article_i18n where article_id = a.id order by locale limit 1
                ) tf on true
                left join cms_category c on c.id = a.category_id and c.deleted_at is null
                left join cms_category_i18n ci on ci.category_id = c.id and ci.locale = ?
                left join cms_category_i18n cz on cz.category_id = c.id and cz.locale = 'zh-CN'
                left join lateral (
                    select name from cms_category_i18n where category_id = c.id order by locale limit 1
                ) cf on true
                """ + where + " order by a.published_at desc nulls last, a.created_at desc limit ? offset ?";

        List<Object> selectArgs = new ArrayList<>();
        selectArgs.add(locale);
        selectArgs.add(locale);
        selectArgs.addAll(filters);
        selectArgs.add(size);
        selectArgs.add((long) number * size);

        List<PublicArticleRow> rows = jdbc.query(select, (rs, rowNum) -> new PublicArticleRow(
                rs.getObject("id", UUID.class), rs.getString("slug"), rs.getString("title"),
                rs.getString("summary"), rs.getObject("cover_file_id", UUID.class),
                toInstant(rs.getTimestamp("published_at")), rs.getString("category_code"),
                rs.getString("category_name")), selectArgs.toArray());
        Map<UUID, List<String>> tagsByArticle = tagCodes(rows.stream().map(PublicArticleRow::id).toList());
        List<PublicArticleView> items = rows.stream()
                .map(row -> new PublicArticleView(row.id(), row.slug(), row.title(), row.summary(),
                        row.coverFileId(), row.publishedAt(), row.categoryCode(), row.categoryName(),
                        tagsByArticle.getOrDefault(row.id(), List.of())))
                .toList();

        String countSql = "select count(*) from cms_article a"
                + " left join cms_category c on c.id = a.category_id and c.deleted_at is null" + where;
        Long total = jdbc.queryForObject(countSql, Long.class, filters.toArray());
        return new PageResult<>(items, new PageResult.PageMeta(number, size, total == null ? 0 : total));
    }

    /** 门户文章详情：未发布或不存在一律 404，避免暴露草稿内容。 */
    @Transactional(readOnly = true)
    public PublicArticleDetail publicBySlug(String slug, String locale) {
        List<PublicArticleDetail> rows = jdbc.query("""
                select a.id, a.slug, a.cover_file_id, a.published_at,
                       coalesce(ti.title, tz.title, tf.title) as title,
                       coalesce(ti.summary, tz.summary, tf.summary) as summary,
                       coalesce(ti.body, tz.body, tf.body) as body,
                       c.code as category_code,
                       coalesce(ci.name, cz.name, cf.name) as category_name
                from cms_article a
                left join cms_article_i18n ti on ti.article_id = a.id and ti.locale = ?
                left join cms_article_i18n tz on tz.article_id = a.id and tz.locale = 'zh-CN'
                left join lateral (
                    select title, summary, body from cms_article_i18n where article_id = a.id order by locale limit 1
                ) tf on true
                left join cms_category c on c.id = a.category_id and c.deleted_at is null
                left join cms_category_i18n ci on ci.category_id = c.id and ci.locale = ?
                left join cms_category_i18n cz on cz.category_id = c.id and cz.locale = 'zh-CN'
                left join lateral (
                    select name from cms_category_i18n where category_id = c.id order by locale limit 1
                ) cf on true
                where a.slug = ? and a.status = 'PUBLISHED' and a.deleted_at is null
                """, (rs, rowNum) -> new PublicArticleDetail(
                rs.getObject("id", UUID.class), rs.getString("slug"), rs.getString("title"),
                rs.getString("summary"), rs.getString("body"), rs.getObject("cover_file_id", UUID.class),
                toInstant(rs.getTimestamp("published_at")), rs.getString("category_code"),
                rs.getString("category_name"), List.of()), locale, locale, slug);
        if (rows.isEmpty()) {
            throw new ApiException(CommonErrors.CONTENT_ARTICLE_NOT_FOUND);
        }
        PublicArticleDetail detail = rows.get(0);
        return new PublicArticleDetail(detail.id(), detail.slug(), detail.title(), detail.summary(),
                detail.body(), detail.coverFileId(), detail.publishedAt(), detail.categoryCode(),
                detail.categoryName(), tagCodes(List.of(detail.id())).getOrDefault(detail.id(), List.of()));
    }

    private List<UUID> keywordArticleIds(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        String like = like(keyword);
        return jdbc.queryForList("""
                select distinct article_id from cms_article_i18n
                 where title ilike ? or coalesce(summary, '') ilike ?
                """, UUID.class, like, like);
    }

    private Map<UUID, List<String>> tagCodes(List<UUID> articleIds) {
        Map<UUID, List<String>> result = new HashMap<>();
        if (articleIds.isEmpty()) {
            return result;
        }
        String placeholders = String.join(", ", articleIds.stream().map(id -> "?").toList());
        jdbc.query("""
                select at.article_id, t.code
                  from cms_article_tag at
                  join cms_tag t on t.id = at.tag_id
                 where at.article_id in (""" + placeholders + ") and t.deleted_at is null order by t.code",
                rs -> {
                    result.computeIfAbsent(rs.getObject("article_id", UUID.class), id -> new ArrayList<>())
                            .add(rs.getString("code"));
                }, articleIds.toArray());
        return result;
    }

    private List<TagView> articleTags(UUID articleId) {
        return jdbc.query("""
                select t.id, t.code, t.created_at,
                       (select count(*) from cms_article_tag x
                          join cms_article a on a.id = x.article_id and a.deleted_at is null
                         where x.tag_id = t.id) as article_count
                  from cms_article_tag at
                  join cms_tag t on t.id = at.tag_id
                 where at.article_id = ? and t.deleted_at is null
                 order by t.code
                """, (rs, rowNum) -> new TagView(rs.getObject("id", UUID.class), rs.getString("code"),
                rs.getLong("article_count"), toInstant(rs.getTimestamp("created_at"))), articleId);
    }

    private ArticleView view(CmsArticle article, List<TagView> tags) {
        return new ArticleView(article.getId(), article.getSlug(), article.getStatus(),
                article.getCategoryId(), article.getCoverFileId(), article.getPublishedAt(), tags,
                translations(article.getId()));
    }

    private List<ArticleTranslation> translations(UUID articleId) {
        return jdbc.query("select locale, title, summary, body from cms_article_i18n where article_id = ? order by locale",
                (rs, rowNum) -> new ArticleTranslation(rs.getString("locale"), rs.getString("title"),
                        rs.getString("summary"), rs.getString("body")), articleId);
    }

    private void replaceTranslations(UUID articleId, List<ArticleTranslation> translations) {
        jdbc.update("delete from cms_article_i18n where article_id = ?", articleId);
        for (ArticleTranslation translation : translations) {
            jdbc.update("""
                    insert into cms_article_i18n (article_id, locale, title, summary, body)
                    values (?, ?, ?, ?, ?)
                    """, articleId, translation.locale(), translation.title(), translation.summary(),
                    translation.body());
        }
    }

    private void requireTranslations(List<ArticleTranslation> translations) {
        if (translations == null || translations.isEmpty()) {
            throw new ApiException(CommonErrors.VALIDATION_FAILED);
        }
        for (ArticleTranslation translation : translations) {
            if (translation.locale() == null || translation.locale().isBlank()
                    || translation.title() == null || translation.title().isBlank()) {
                throw new ApiException(CommonErrors.VALIDATION_FAILED);
            }
        }
    }

    private void requireCategory(UUID categoryId) {
        if (categoryId != null && categories.findByIdAndDeletedAtIsNull(categoryId).isEmpty()) {
            throw new ApiException(CommonErrors.CONTENT_CATEGORY_NOT_FOUND);
        }
    }

    private void requireTag(UUID tagId) {
        Long count = jdbc.queryForObject(
                "select count(*) from cms_tag where id = ? and deleted_at is null", Long.class, tagId);
        if (count == null || count == 0) {
            throw new ApiException(CommonErrors.CONTENT_TAG_NOT_FOUND);
        }
    }

    private CmsArticle require(UUID id) {
        return articles.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ApiException(CommonErrors.CONTENT_ARTICLE_NOT_FOUND));
    }

    private static int clampSize(int pageSize) {
        return Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);
    }

    private static String like(String keyword) {
        return "%" + keyword.strip() + "%";
    }

    private static Instant toInstant(java.sql.Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private record PublicArticleRow(UUID id, String slug, String title, String summary, UUID coverFileId,
                                    Instant publishedAt, String categoryCode, String categoryName) {
    }
}
