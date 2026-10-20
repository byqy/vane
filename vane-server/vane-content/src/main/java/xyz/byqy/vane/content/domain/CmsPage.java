package xyz.byqy.vane.content.domain;

import xyz.byqy.vane.common.persistence.AuditableEntity;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** cms_page（05 §6.1）：正文按语言存 cms_page_i18n。 */
@Entity
@Table(name = "cms_page")
public class CmsPage extends AuditableEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String slug;

    @Column(nullable = false)
    private String status;

    @Column(name = "cover_file_id")
    private UUID coverFileId;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "author_id")
    private UUID authorId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected CmsPage() {
    }

    public static CmsPage create(UUID id, String slug, UUID coverFileId, UUID authorId) {
        CmsPage page = new CmsPage();
        page.id = id;
        page.slug = slug;
        page.status = "DRAFT";
        page.coverFileId = coverFileId;
        page.authorId = authorId;
        page.createdAt = Instant.now();
        return page;
    }

    public void update(UUID coverFileId) {
        this.coverFileId = coverFileId;
    }

    public void publish(Instant now) {
        this.status = "PUBLISHED";
        this.publishedAt = now;
    }

    public void archive() {
        this.status = "ARCHIVED";
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

    public String getStatus() {
        return status;
    }

    public UUID getCoverFileId() {
        return coverFileId;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }
}

