package xyz.byqy.vane.content.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.test.util.ReflectionTestUtils;

import xyz.byqy.vane.common.error.ApiException;
import xyz.byqy.vane.common.error.CommonErrors;
import xyz.byqy.vane.content.domain.CmsArticle;
import xyz.byqy.vane.content.domain.CmsArticleRepository;
import xyz.byqy.vane.content.domain.CmsCategory;
import xyz.byqy.vane.content.domain.CmsCategoryRepository;
import xyz.byqy.vane.content.domain.CmsTag;
import xyz.byqy.vane.content.domain.CmsTagRepository;

/**
 * 内容体系服务层规则测试：校验、冲突、删除保护与分页上限，不启动 Spring 上下文。
 */
class ArticleCategoryTagServiceTest {

    private final CmsCategoryRepository categories = mock(CmsCategoryRepository.class);
    private final CmsArticleRepository articles = mock(CmsArticleRepository.class);
    private final CmsTagRepository tags = mock(CmsTagRepository.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);

    private final CategoryService categoryService = new CategoryService(categories, articles, jdbc);
    private final ArticleService articleService = new ArticleService(articles, categories, jdbc);
    private final TagService tagService = new TagService(tags, jdbc);

    private static List<CategoryTranslation> categoryTranslations() {
        return List.of(new CategoryTranslation("zh-CN", "新闻", "分类描述"));
    }

    private static List<ArticleTranslation> articleTranslations() {
        return List.of(new ArticleTranslation("zh-CN", "标题", "摘要", "<p>正文</p>"));
    }

    private static void assertError(org.assertj.core.api.ThrowableAssert.ThrowingCallable call,
                                    CommonErrors expected) {
        assertThatThrownBy(call)
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).errorCode())
                .isEqualTo(expected);
    }

    // ---------- 分类 ----------

    @Test
    void categoryRejectsEmptyTranslations() {
        assertError(() -> categoryService.create("news", 0, null, List.of()), CommonErrors.VALIDATION_FAILED);
    }

    @Test
    void categoryRejectsTranslationWithoutName() {
        assertError(() -> categoryService.create("news", 0, null,
                List.of(new CategoryTranslation("zh-CN", " ", null))), CommonErrors.VALIDATION_FAILED);
    }

    @Test
    void categoryRejectsDuplicateCode() {
        when(categories.existsByCodeAndDeletedAtIsNull("news")).thenReturn(true);

        assertError(() -> categoryService.create("news", 0, null, categoryTranslations()),
                CommonErrors.CONTENT_CATEGORY_CODE_TAKEN);
    }

    @Test
    void categoryRejectsUnknownStatus() {
        when(categories.existsByCodeAndDeletedAtIsNull("news")).thenReturn(false);

        assertError(() -> categoryService.create("news", 0, "BROKEN", categoryTranslations()),
                CommonErrors.VALIDATION_FAILED);
    }

    @Test
    void categoryCreateDefaultsToActive() {
        when(categories.existsByCodeAndDeletedAtIsNull("news")).thenReturn(false);
        when(categories.saveAndFlush(any(CmsCategory.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CategoryView created = categoryService.create("news", 5, null, categoryTranslations());

        assertThat(created.code()).isEqualTo("news");
        assertThat(created.status()).isEqualTo("ACTIVE");
        assertThat(created.sortNo()).isEqualTo(5);
        assertThat(created.parentId()).isNull();
        assertThat(created.translations()).hasSize(1);
    }

    @Test
    void categoryUpdateRejectsDuplicateCodeOfAnotherCategory() {
        UUID id = UUID.randomUUID();
        CmsCategory existing = CmsCategory.create(UUID.randomUUID(), "news", 0, "ACTIVE");
        when(categories.findByIdAndDeletedAtIsNull(id)).thenReturn(Optional.of(existing));
        when(categories.findByCodeAndDeletedAtIsNull("guides")).thenReturn(Optional.of(existing));

        assertError(() -> categoryService.update(id, "guides", 0, "ACTIVE", categoryTranslations()),
                CommonErrors.CONTENT_CATEGORY_CODE_TAKEN);
    }

    @Test
    void categoryUpdateKeepsSameCode() {
        UUID id = UUID.randomUUID();
        CmsCategory existing = CmsCategory.create(id, "news", 0, "ACTIVE");
        when(categories.findByIdAndDeletedAtIsNull(id)).thenReturn(Optional.of(existing));
        when(categories.save(any(CmsCategory.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CategoryView updated = categoryService.update(id, "news", 3, "DISABLED", categoryTranslations());

        assertThat(updated.status()).isEqualTo("DISABLED");
        assertThat(updated.sortNo()).isEqualTo(3);
    }

    @Test
    void categoryDeleteRejectedWhenArticlesExist() {
        UUID id = UUID.randomUUID();
        when(categories.findByIdAndDeletedAtIsNull(id))
                .thenReturn(Optional.of(CmsCategory.create(id, "news", 0, "ACTIVE")));
        when(articles.existsByCategoryIdAndDeletedAtIsNull(id)).thenReturn(true);

        assertError(() -> categoryService.delete(id), CommonErrors.CONTENT_CATEGORY_HAS_ARTICLES);
    }

    @Test
    void categoryDeleteMissingIs404() {
        UUID id = UUID.randomUUID();
        when(categories.findByIdAndDeletedAtIsNull(id)).thenReturn(Optional.empty());

        assertError(() -> categoryService.delete(id), CommonErrors.CONTENT_CATEGORY_NOT_FOUND);
    }

    @Test
    void adminTreeNestsChildren() {
        UUID parentId = UUID.randomUUID();
        UUID childId = UUID.randomUUID();
        CmsCategory parent = CmsCategory.create(parentId, "news", 0, "ACTIVE");
        CmsCategory child = CmsCategory.create(childId, "guides", 10, "ACTIVE");
        ReflectionTestUtils.setField(child, "parentId", parentId);
        when(categories.findAllByDeletedAtIsNullOrderBySortNoAscCodeAsc()).thenReturn(List.of(parent, child));
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());

        List<CategoryNodeView> tree = categoryService.adminTree();

        assertThat(tree).hasSize(1);
        assertThat(tree.get(0).code()).isEqualTo("news");
        assertThat(tree.get(0).children()).hasSize(1);
        assertThat(tree.get(0).children().get(0).code()).isEqualTo("guides");
    }

    // ---------- 文章 ----------

    @Test
    void articleRejectsEmptyTranslations() {
        assertError(() -> articleService.create("post", null, null, List.of(), null),
                CommonErrors.VALIDATION_FAILED);
    }

    @Test
    void articleRejectsTranslationWithoutTitle() {
        assertError(() -> articleService.create("post", null, null,
                List.of(new ArticleTranslation("zh-CN", " ", null, null)), null),
                CommonErrors.VALIDATION_FAILED);
    }

    @Test
    void articleRejectsDuplicateSlug() {
        when(articles.existsBySlugAndDeletedAtIsNull("post")).thenReturn(true);

        assertError(() -> articleService.create("post", null, null, articleTranslations(), null),
                CommonErrors.CONTENT_SLUG_TAKEN);
    }

    @Test
    void articleRejectsUnknownCategory() {
        when(articles.existsBySlugAndDeletedAtIsNull("post")).thenReturn(false);
        UUID categoryId = UUID.randomUUID();
        when(categories.findByIdAndDeletedAtIsNull(categoryId)).thenReturn(Optional.empty());

        assertError(() -> articleService.create("post", categoryId, null, articleTranslations(), null),
                CommonErrors.CONTENT_CATEGORY_NOT_FOUND);
    }

    @Test
    void articleCreateStartsAsDraft() {
        when(articles.existsBySlugAndDeletedAtIsNull("post")).thenReturn(false);
        when(articles.saveAndFlush(any(CmsArticle.class))).thenAnswer(invocation -> invocation.getArgument(0));
        UUID authorId = UUID.randomUUID();

        ArticleView created = articleService.create("post", null, null, articleTranslations(), authorId);

        assertThat(created.slug()).isEqualTo("post");
        assertThat(created.status()).isEqualTo("DRAFT");
        assertThat(created.tags()).isEmpty();
    }

    @Test
    void articlePublishMissingIs404() {
        UUID id = UUID.randomUUID();
        when(articles.findByIdAndDeletedAtIsNull(id)).thenReturn(Optional.empty());

        assertError(() -> articleService.publish(id), CommonErrors.CONTENT_ARTICLE_NOT_FOUND);
    }

    @Test
    void articleDeleteMissingIs404() {
        UUID id = UUID.randomUUID();
        when(articles.findByIdAndDeletedAtIsNull(id)).thenReturn(Optional.empty());

        assertError(() -> articleService.delete(id), CommonErrors.CONTENT_ARTICLE_NOT_FOUND);
    }

    @Test
    void articleReplaceTagsRejectsUnknownTag() {
        UUID id = UUID.randomUUID();
        when(articles.findByIdAndDeletedAtIsNull(id))
                .thenReturn(Optional.of(CmsArticle.create(id, "post", null, null, null)));
        UUID tagId = UUID.randomUUID();
        when(jdbc.queryForObject(anyString(), any(Class.class), any(Object[].class))).thenReturn(0L);

        assertError(() -> articleService.replaceTags(id, List.of(tagId), null),
                CommonErrors.CONTENT_TAG_NOT_FOUND);
    }

    @Test
    void articleReplaceTagsWritesJoinRows() {
        UUID id = UUID.randomUUID();
        UUID tagId = UUID.randomUUID();
        when(articles.findByIdAndDeletedAtIsNull(id))
                .thenReturn(Optional.of(CmsArticle.create(id, "post", null, null, null)));
        when(jdbc.queryForObject(anyString(), any(Class.class), any(Object[].class))).thenReturn(1L);
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());

        List<TagView> result = articleService.replaceTags(id, List.of(tagId, tagId), null);

        assertThat(result).isEmpty();
    }

    @Test
    void adminPageClampsSizeAndRejectsUnknownKeyword() {
        when(jdbc.queryForList(anyString(), any(Class.class), any(Object[].class))).thenReturn(List.of());
        when(articles.findAll(any(Specification.class), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 100), 0));

        PageResult<ArticleView> page = articleService.adminPage(0, 500, null, null, "不存在的关键词", null);

        assertThat(page.page().size()).isEqualTo(100);
        assertThat(page.items()).isEmpty();
    }

    @Test
    void publicBySlugMissingIs404() {
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());

        assertError(() -> articleService.publicBySlug("missing", "zh-CN"), CommonErrors.CONTENT_ARTICLE_NOT_FOUND);
    }

    // ---------- 标签 ----------

    @Test
    void tagRejectsDuplicateCode() {
        when(tags.existsByCodeAndDeletedAtIsNull("guides")).thenReturn(true);

        assertError(() -> tagService.create("guides"), CommonErrors.CONTENT_TAG_CODE_TAKEN);
    }

    @Test
    void tagCreateTrimsCode() {
        when(tags.existsByCodeAndDeletedAtIsNull("guides")).thenReturn(false);
        when(tags.saveAndFlush(any(CmsTag.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TagView created = tagService.create("  guides  ");

        assertThat(created.code()).isEqualTo("guides");
        assertThat(created.articleCount()).isZero();
    }

    @Test
    void tagDeleteMissingIs404() {
        UUID id = UUID.randomUUID();
        when(tags.findByIdAndDeletedAtIsNull(id)).thenReturn(Optional.empty());

        assertError(() -> tagService.delete(id), CommonErrors.CONTENT_TAG_NOT_FOUND);
    }
}
