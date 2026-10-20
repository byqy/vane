package xyz.byqy.vane.content.application;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import xyz.byqy.vane.common.error.ApiException;
import xyz.byqy.vane.common.error.CommonErrors;
import xyz.byqy.vane.content.domain.CmsArticleRepository;
import xyz.byqy.vane.content.domain.CmsCategory;
import xyz.byqy.vane.content.domain.CmsCategoryRepository;

/**
 * 文章分类（ADM-08）：业务上只允许一级分类，{@code parent_id} 保留但 API 不写；
 * 名称与描述按语言存 cms_category_i18n。
 */
@Service
public class CategoryService {

    private static final String DEFAULT_STATUS = "ACTIVE";

    private final CmsCategoryRepository categories;
    private final CmsArticleRepository articles;
    private final JdbcTemplate jdbc;

    public CategoryService(CmsCategoryRepository categories, CmsArticleRepository articles, JdbcTemplate jdbc) {
        this.categories = categories;
        this.articles = articles;
        this.jdbc = jdbc;
    }

    @Transactional
    public CategoryView create(String code, int sortNo, String status, List<CategoryTranslation> translations) {
        requireTranslations(translations);
        if (categories.existsByCodeAndDeletedAtIsNull(code)) {
            throw new ApiException(CommonErrors.CONTENT_CATEGORY_CODE_TAKEN);
        }
        CmsCategory category = CmsCategory.create(UUID.randomUUID(), code, sortNo, normalizeStatus(status));
        categories.saveAndFlush(category);
        replaceTranslations(category.getId(), translations);
        return view(category, translations);
    }

    @Transactional
    public CategoryView update(UUID id, String code, int sortNo, String status,
                               List<CategoryTranslation> translations) {
        CmsCategory category = require(id);
        requireTranslations(translations);
        categories.findByCodeAndDeletedAtIsNull(code)
                .filter(other -> !other.getId().equals(id))
                .ifPresent(other -> {
                    throw new ApiException(CommonErrors.CONTENT_CATEGORY_CODE_TAKEN);
                });
        category.update(code, sortNo, normalizeStatus(status));
        categories.save(category);
        replaceTranslations(id, translations);
        return view(category, translations);
    }

    /** 删除保护：分类下仍有文章时拒绝删除（409）。 */
    @Transactional
    public void delete(UUID id) {
        CmsCategory category = require(id);
        if (articles.existsByCategoryIdAndDeletedAtIsNull(id)) {
            throw new ApiException(CommonErrors.CONTENT_CATEGORY_HAS_ARTICLES);
        }
        category.softDelete();
        categories.save(category);
    }

    /** 后台分类树；当前只有一级，children 恒为空列表。 */
    @Transactional(readOnly = true)
    public List<CategoryNodeView> adminTree() {
        List<CmsCategory> all = categories.findAllByDeletedAtIsNullOrderBySortNoAscCodeAsc();
        Map<UUID, CategoryNodeView> nodes = new LinkedHashMap<>();
        for (CmsCategory category : all) {
            nodes.put(category.getId(), new CategoryNodeView(category.getId(), category.getParentId(),
                    category.getCode(), category.getSortNo(), category.getStatus(),
                    translations(category.getId()), new ArrayList<>()));
        }
        return nest(nodes);
    }

    /** 门户分类树：只含启用分类，带已发布文章数，按请求语言回退取名称与描述。 */
    @Transactional(readOnly = true)
    public List<PublicCategoryView> publicTree(String locale) {
        List<CategoryRow> rows = jdbc.query("""
                select c.id, c.parent_id, c.code,
                       coalesce(ci.name, cz.name, cf.name) as name,
                       coalesce(ci.description, cz.description, cf.description) as description,
                       (select count(*) from cms_article a
                         where a.category_id = c.id and a.status = 'PUBLISHED' and a.deleted_at is null) as article_count
                from cms_category c
                left join cms_category_i18n ci on ci.category_id = c.id and ci.locale = ?
                left join cms_category_i18n cz on cz.category_id = c.id and cz.locale = 'zh-CN'
                left join lateral (
                    select name, description from cms_category_i18n where category_id = c.id order by locale limit 1
                ) cf on true
                where c.status = 'ACTIVE' and c.deleted_at is null
                order by c.sort_no, c.code
                """, (rs, rowNum) -> new CategoryRow(
                rs.getObject("id", UUID.class),
                rs.getObject("parent_id", UUID.class),
                rs.getString("code"), rs.getString("name"), rs.getString("description"),
                rs.getLong("article_count")), locale);

        Map<UUID, PublicCategoryView> nodes = new LinkedHashMap<>();
        for (CategoryRow row : rows) {
            nodes.put(row.id(), new PublicCategoryView(row.id(), row.code(), row.name(), row.description(),
                    row.articleCount(), new ArrayList<>()));
        }
        List<PublicCategoryView> roots = new ArrayList<>();
        for (CategoryRow row : rows) {
            PublicCategoryView node = nodes.get(row.id());
            PublicCategoryView parent = row.parentId() == null ? null : nodes.get(row.parentId());
            if (parent == null) {
                roots.add(node);
            } else {
                parent.children().add(node);
            }
        }
        return roots;
    }

    private List<CategoryNodeView> nest(Map<UUID, CategoryNodeView> nodes) {
        List<CategoryNodeView> roots = new ArrayList<>();
        for (CategoryNodeView node : nodes.values()) {
            CategoryNodeView parent = node.parentId() == null ? null : nodes.get(node.parentId());
            if (parent == null) {
                roots.add(node);
            } else {
                parent.children().add(node);
            }
        }
        return roots;
    }

    private CategoryView view(CmsCategory category, List<CategoryTranslation> translations) {
        return new CategoryView(category.getId(), category.getParentId(), category.getCode(),
                category.getSortNo(), category.getStatus(), translations);
    }

    private List<CategoryTranslation> translations(UUID categoryId) {
        return jdbc.query("select locale, name, description from cms_category_i18n where category_id = ? order by locale",
                (rs, rowNum) -> new CategoryTranslation(rs.getString("locale"), rs.getString("name"),
                        rs.getString("description")), categoryId);
    }

    private void replaceTranslations(UUID categoryId, List<CategoryTranslation> translations) {
        jdbc.update("delete from cms_category_i18n where category_id = ?", categoryId);
        for (CategoryTranslation translation : translations) {
            jdbc.update("insert into cms_category_i18n (category_id, locale, name, description) values (?, ?, ?, ?)",
                    categoryId, translation.locale(), translation.name(), translation.description());
        }
    }

    private void requireTranslations(List<CategoryTranslation> translations) {
        if (translations == null || translations.isEmpty()) {
            throw new ApiException(CommonErrors.VALIDATION_FAILED);
        }
        for (CategoryTranslation translation : translations) {
            if (translation.locale() == null || translation.locale().isBlank()
                    || translation.name() == null || translation.name().isBlank()) {
                throw new ApiException(CommonErrors.VALIDATION_FAILED);
            }
        }
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return DEFAULT_STATUS;
        }
        if (!"ACTIVE".equals(status) && !"DISABLED".equals(status)) {
            throw new ApiException(CommonErrors.VALIDATION_FAILED);
        }
        return status;
    }

    private CmsCategory require(UUID id) {
        return categories.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ApiException(CommonErrors.CONTENT_CATEGORY_NOT_FOUND));
    }

    private record CategoryRow(UUID id, UUID parentId, String code, String name, String description,
                               long articleCount) {
    }
}
