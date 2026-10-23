package xyz.byqy.vane.content.application;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import xyz.byqy.vane.common.error.ApiException;
import xyz.byqy.vane.common.error.CommonErrors;

/**
 * 产品（ADR-025）：官网产品中心的数据来源。
 *
 * <p>读取沿用内容模块的回退顺序：请求语言 → zh-CN → 首个可用语言；
 * 门户只可见 {@code PUBLISHED} 且未删除的产品。</p>
 */
@Service
public class ProductService {

    private static final String DEFAULT_LOCALE = "zh-CN";
    private static final int MAX_PAGE_SIZE = 100;
    private static final TypeReference<List<ProductFeature>> FEATURES = new TypeReference<>() {
    };

    private static final String COLUMNS = """
            p.id, p.code, p.slug, p.group_code, p.cover_file_id, p.video_file_id, p.status, p.sort_no,
            p.published_at, p.created_at, p.updated_at
            """;

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final Clock clock;

    public ProductService(JdbcTemplate jdbc, ObjectMapper json, Clock clock) {
        this.jdbc = jdbc;
        this.json = json;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ 门户

    @Transactional(readOnly = true)
    public PageResult<PublicProductView> publicPage(String group, String locale, int number, int size) {
        int pageNumber = Math.max(number, 0);
        int pageSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        String groupFilter = group == null || group.isBlank() ? null : group;
        List<ProductRow> rows = jdbc.query("""
                select %s
                  from cms_product p
                 where p.status = 'PUBLISHED'
                   and p.deleted_at is null
                   and (cast(? as text) is null or p.group_code = ?)
                 order by p.sort_no asc, p.published_at desc nulls last
                 limit ? offset ?
                """.formatted(COLUMNS), (rs, rowNum) -> row(rs), groupFilter, groupFilter, pageSize,
                (long) pageNumber * pageSize);
        Long total = jdbc.queryForObject("""
                select count(*)
                  from cms_product p
                 where p.status = 'PUBLISHED'
                   and p.deleted_at is null
                   and (cast(? as text) is null or p.group_code = ?)
                """, Long.class, groupFilter, groupFilter);
        Map<UUID, ProductTranslation> texts = pickTranslations(locale,
                rows.stream().map(ProductRow::id).toList());
        List<PublicProductView> items = rows.stream()
                .map(item -> {
                    ProductTranslation text = texts.get(item.id());
                    return new PublicProductView(item.id().toString(), item.code(), item.slug(), item.group(),
                            textOf(item.coverFileId()), textOf(item.videoFileId()),
                            text == null ? item.code() : text.name(),
                            text == null ? null : text.summary(),
                            item.sortNo(), instant(item.publishedAt()));
                })
                .toList();
        return new PageResult<>(items, new PageResult.PageMeta(pageNumber, pageSize, total == null ? 0 : total));
    }

    @Transactional(readOnly = true)
    public PublicProductDetail publicBySlug(String slug, String locale) {
        ProductRow row = jdbc.query("""
                select %s
                  from cms_product p
                 where p.slug = ? and p.status = 'PUBLISHED' and p.deleted_at is null
                """.formatted(COLUMNS), (rs, rowNum) -> row(rs), slug).stream().findFirst()
                .orElseThrow(() -> new ApiException(CommonErrors.CONTENT_PRODUCT_NOT_FOUND));
        ProductTranslation text = pickTranslations(locale, List.of(row.id())).get(row.id());
        if (text == null) {
            throw new ApiException(CommonErrors.CONTENT_PRODUCT_NOT_FOUND);
        }
        return new PublicProductDetail(row.id().toString(), row.code(), row.slug(), row.group(),
                textOf(row.coverFileId()), textOf(row.videoFileId()),
                text.name(), text.summary(), text.body(), text.features(),
                publicMedia(row.id()), row.sortNo(), instant(row.publishedAt()));
    }

    @Transactional(readOnly = true)
    public List<ProductLink> publishedLinks() {
        return jdbc.query("""
                select slug, published_at
                  from cms_product
                 where status = 'PUBLISHED' and deleted_at is null
                 order by sort_no asc, published_at desc nulls last
                """, (rs, rowNum) -> new ProductLink(rs.getString("slug"),
                rs.getTimestamp("published_at") == null ? null : rs.getTimestamp("published_at").toInstant()));
    }

    // ------------------------------------------------------------------ 后台

    @Transactional(readOnly = true)
    public PageResult<ProductView> adminPage(int number, int size, String status, String keyword) {
        int pageNumber = Math.max(number, 0);
        int pageSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        String statusFilter = status == null || status.isBlank() ? null : status;
        String keywordFilter = keyword == null || keyword.isBlank() ? null : keyword.trim();
        List<ProductRow> rows = jdbc.query("""
                select %s
                  from cms_product p
                 where p.deleted_at is null
                   and (cast(? as text) is null or p.status = ?)
                   and (cast(? as text) is null
                        or p.code ilike '%%' || ? || '%%'
                        or exists (select 1 from cms_product_i18n i
                                    where i.product_id = p.id and i.name ilike '%%' || ? || '%%'))
                 order by p.sort_no asc, p.created_at desc
                 limit ? offset ?
                """.formatted(COLUMNS), (rs, rowNum) -> row(rs), statusFilter, statusFilter, keywordFilter,
                keywordFilter, keywordFilter, pageSize, (long) pageNumber * pageSize);
        Long total = jdbc.queryForObject("""
                select count(*)
                  from cms_product p
                 where p.deleted_at is null
                   and (cast(? as text) is null or p.status = ?)
                   and (cast(? as text) is null
                        or p.code ilike '%%' || ? || '%%'
                        or exists (select 1 from cms_product_i18n i
                                    where i.product_id = p.id and i.name ilike '%%' || ? || '%%'))
                """, Long.class, statusFilter, statusFilter, keywordFilter, keywordFilter, keywordFilter);
        Map<UUID, List<ProductTranslation>> texts = allTranslations(rows.stream().map(ProductRow::id).toList());
        List<ProductView> items = rows.stream()
                .map(item -> toView(item, texts.getOrDefault(item.id(), List.of())))
                .toList();
        return new PageResult<>(items, new PageResult.PageMeta(pageNumber, pageSize, total == null ? 0 : total));
    }

    @Transactional(readOnly = true)
    public ProductDetailView adminGet(UUID id) {
        ProductRow row = require(id);
        return new ProductDetailView(row.id(), row.code(), row.slug(), row.group(), uuid(row.coverFileId()),
                uuid(row.videoFileId()), row.status(), row.sortNo(), row.publishedAt(), row.createdAt(),
                row.updatedAt(), allTranslations(List.of(id)).getOrDefault(id, List.of()), media(id));
    }

    @Transactional
    public ProductView create(ProductCommand command, UUID actor) {
        UUID id = UUID.randomUUID();
        try {
            jdbc.update("""
                    insert into cms_product (id, code, slug, group_code, cover_file_id, video_file_id, status,
                                             sort_no, created_by, updated_by)
                    values (?, ?, ?, ?, cast(? as uuid), cast(? as uuid), ?, ?, cast(? as uuid), cast(? as uuid))
                    """, id, command.code(), command.slug(), command.group(), command.coverFileId(),
                    command.videoFileId(), statusOrDefault(command.status()), command.sortNo(), actor, actor);
        } catch (DuplicateKeyException e) {
            throw new ApiException(CommonErrors.CONTENT_PRODUCT_CODE_TAKEN);
        }
        replaceTranslations(id, command.translations());
        replaceMediaRows(id, command.media());
        return toView(require(id), allTranslations(List.of(id)).getOrDefault(id, List.of()));
    }

    @Transactional
    public ProductView update(UUID id, ProductCommand command) {
        require(id);
        try {
            jdbc.update("""
                    update cms_product
                       set code = ?, slug = ?, group_code = ?, cover_file_id = cast(? as uuid),
                           video_file_id = cast(? as uuid), status = ?, sort_no = ?, updated_at = now()
                     where id = ?
                    """, command.code(), command.slug(), command.group(), command.coverFileId(),
                    command.videoFileId(), statusOrDefault(command.status()), command.sortNo(), id);
        } catch (DuplicateKeyException e) {
            throw new ApiException(CommonErrors.CONTENT_PRODUCT_CODE_TAKEN);
        }
        replaceTranslations(id, command.translations());
        if (!command.media().isEmpty()) {
            replaceMediaRows(id, command.media());
        }
        return toView(require(id), allTranslations(List.of(id)).getOrDefault(id, List.of()));
    }

    @Transactional
    public ProductView publish(UUID id) {
        require(id);
        jdbc.update("""
                update cms_product
                   set status = 'PUBLISHED', published_at = coalesce(published_at, now()), updated_at = now()
                 where id = ?
                """, id);
        return toView(require(id), allTranslations(List.of(id)).getOrDefault(id, List.of()));
    }

    @Transactional
    public ProductView unpublish(UUID id) {
        require(id);
        jdbc.update("update cms_product set status = 'ARCHIVED', updated_at = now() where id = ?", id);
        return toView(require(id), allTranslations(List.of(id)).getOrDefault(id, List.of()));
    }

    @Transactional
    public void delete(UUID id) {
        require(id);
        jdbc.update("update cms_product set deleted_at = now(), updated_at = now() where id = ?", id);
    }

    @Transactional
    public void replaceMedia(UUID id, List<PublicProductMedia> media) {
        require(id);
        replaceMediaRows(id, media);
    }

    // ------------------------------------------------------------------ 内部

    private void replaceTranslations(UUID id, List<ProductTranslation> translations) {
        if (translations.isEmpty()) {
            throw new ApiException(CommonErrors.VALIDATION_FAILED);
        }
        jdbc.update("delete from cms_product_i18n where product_id = ?", id);
        for (ProductTranslation translation : translations) {
            if (translation.locale() == null || translation.name() == null) {
                throw new ApiException(CommonErrors.VALIDATION_FAILED);
            }
            jdbc.update("""
                    insert into cms_product_i18n (product_id, locale, name, summary, body, features, updated_at)
                    values (?, ?, ?, ?, ?, cast(? as jsonb), now())
                    """, id, translation.locale(), translation.name(), translation.summary(), translation.body(),
                    featuresJson(translation.features()));
        }
    }

    private void replaceMediaRows(UUID id, List<PublicProductMedia> media) {
        jdbc.update("delete from cms_product_media where product_id = ?", id);
        List<PublicProductMedia> items = media == null ? List.of() : media;
        for (int index = 0; index < items.size(); index++) {
            PublicProductMedia item = items.get(index);
            jdbc.update("""
                    insert into cms_product_media (id, product_id, file_id, kind, sort_no, caption)
                    values (?, ?, cast(? as uuid), ?, ?, ?)
                    """, UUID.randomUUID(), id, item.fileId(), kind(item.kind()), index, item.caption());
        }
    }

    private String featuresJson(List<ProductFeature> features) {
        return json.writeValueAsString(features == null ? List.of() : features);
    }

    private List<ProductFeature> features(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return json.readValue(value, FEATURES);
    }

    private ProductView toView(ProductRow row, List<ProductTranslation> translations) {
        return new ProductView(row.id(), row.code(), row.slug(), row.group(), uuid(row.coverFileId()),
                uuid(row.videoFileId()), row.status(), row.sortNo(), row.publishedAt(), row.createdAt(),
                row.updatedAt(), translations);
    }

    private ProductRow require(UUID id) {
        return jdbc.query("""
                select %s
                  from cms_product p
                 where p.id = ? and p.deleted_at is null
                """.formatted(COLUMNS), (rs, rowNum) -> row(rs), id).stream().findFirst()
                .orElseThrow(() -> new ApiException(CommonErrors.CONTENT_PRODUCT_NOT_FOUND));
    }

    private List<ProductMediaView> media(UUID productId) {
        return jdbc.query("""
                select id, product_id, file_id, kind, sort_no, caption, updated_at
                  from cms_product_media
                 where product_id = ?
                 order by sort_no asc
                """, (rs, rowNum) -> new ProductMediaView(
                rs.getObject("id", UUID.class), rs.getObject("product_id", UUID.class),
                rs.getObject("file_id", UUID.class), rs.getString("kind"), rs.getInt("sort_no"),
                rs.getString("caption"), rs.getTimestamp("updated_at").toInstant()), productId);
    }

    private List<PublicProductMedia> publicMedia(UUID productId) {
        return media(productId).stream()
                .map(item -> new PublicProductMedia(item.fileId().toString(), item.kind(), item.sortNo(),
                        item.caption()))
                .toList();
    }

    /** 按回退顺序挑一条译文：请求语言 → zh-CN → 首个可用。 */
    private Map<UUID, ProductTranslation> pickTranslations(String locale, List<UUID> productIds) {
        Map<UUID, List<ProductTranslation>> all = allTranslations(productIds);
        Map<UUID, ProductTranslation> picked = new LinkedHashMap<>();
        all.forEach((id, rows) -> rows.stream()
                .filter(row -> locale != null && row.locale().equalsIgnoreCase(locale)).findFirst()
                .or(() -> rows.stream().filter(row -> row.locale().equals(DEFAULT_LOCALE)).findFirst())
                .or(() -> rows.stream().findFirst())
                .ifPresent(row -> picked.put(id, row)));
        return picked;
    }

    private Map<UUID, List<ProductTranslation>> allTranslations(List<UUID> productIds) {
        Map<UUID, List<ProductTranslation>> result = new LinkedHashMap<>();
        if (productIds.isEmpty()) {
            return result;
        }
        String placeholders = String.join(",", productIds.stream().map(id -> "?").toList());
        jdbc.query("""
                select product_id, locale, name, summary, body, features
                  from cms_product_i18n
                 where product_id in (%s)
                 order by product_id, locale
                """.formatted(placeholders), rs -> {
            UUID productId = rs.getObject("product_id", UUID.class);
            result.computeIfAbsent(productId, key -> new ArrayList<>()).add(new ProductTranslation(
                    rs.getString("locale"), rs.getString("name"), rs.getString("summary"),
                    rs.getString("body"), features(rs.getString("features"))));
        }, productIds.toArray());
        return result;
    }

    private ProductRow row(ResultSet rs) throws SQLException {
        return new ProductRow(rs.getObject("id", UUID.class), rs.getString("code"), rs.getString("slug"),
                rs.getString("group_code"), rs.getString("cover_file_id"), rs.getString("video_file_id"),
                rs.getString("status"), rs.getInt("sort_no"),
                instantOf(rs, "published_at"), instantOf(rs, "created_at"), instantOf(rs, "updated_at"));
    }

    private static Instant instantOf(ResultSet rs, String column) throws SQLException {
        java.sql.Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static String statusOrDefault(String status) {
        return status == null || status.isBlank() ? "DRAFT" : status;
    }

    private static String kind(String value) {
        return "VIDEO".equalsIgnoreCase(value) ? "VIDEO" : "IMAGE";
    }

    private static String textOf(String value) {
        return value;
    }

    private static UUID uuid(String value) {
        return value == null || value.isBlank() ? null : UUID.fromString(value);
    }

    private static String instant(Instant value) {
        return value == null ? null : value.toString();
    }

    private record ProductRow(UUID id, String code, String slug, String group, String coverFileId,
                              String videoFileId, String status, int sortNo, Instant publishedAt,
                              Instant createdAt, Instant updatedAt) {
    }

    /** sitemap 用：已发布产品的 slug 与发布时间。 */
    public record ProductLink(String slug, Instant publishedAt) {
    }
}
