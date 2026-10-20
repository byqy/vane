package xyz.byqy.vane.content.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import xyz.byqy.vane.common.persistence.AuditableEntity;

/** cms_article（05 §6）：文章主体；标题、摘要与正文按语言存 cms_article_i18n。 */
@Entity
@Table(name = "cms_article")
public class CmsArticle extends AuditableEntity {

    public static final String DRAFT = "DRAFT";
    public static final String PUBLISHED = "PUBLISHED";
    public static final String ARCHIVED = "ARCHIVED";

    @Id
    private UUID id;

    @Column(nullable = false)
    private String slug;

    @Column(name = "category_id")
    private UUID categoryId;

    @Column(name = "cover_file_id")
    private UUID coverFileId;

    @Column(name = "author_id")
    private UUID authorId;

    @Column(nullable = false)
    private String status;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected CmsArticle() {
    }

    public static CmsArticle create(UUID id, String slug, UUID categoryId, UUID coverFileId, UUID authorId) {
        CmsArticle article = new CmsArticle();
        article.id = id;
        article.slug = slug;
        article.categoryId = categoryId;
        article.coverFileId = coverFileId;
        article.authorId = authorId;
        article.status = DRAFT;
        article.createdAt = Instant.now();
        return article;
    }

    public void update(String slug, UUID categoryId, UUID coverFileId) {
        this.slug = slug;
        this.categoryId = categoryId;
        this.coverFileId = coverFileId;
    }

    public void publish(Instant now) {
        this.status = PUBLISHED;
        this.publishedAt = now;
    }

    /** 下线：回到草稿，保留历史首次发布时间由 published_at 记录。 */
    public void unpublish() {
        this.status = DRAFT;
    }

    public void archive() {
        this.status = ARCHIVED;
    }

    public void softDelete() {
        this.deletedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getSlug() {
        return slug;
    }

    public UUID getCategoryId() {
        return categoryId;
    }

    public UUID getCoverFileId() {
        return coverFileId;
    }

    public UUID getAuthorId() {
        return authorId;
    }

    public String getStatus() {
        return status;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }
}
