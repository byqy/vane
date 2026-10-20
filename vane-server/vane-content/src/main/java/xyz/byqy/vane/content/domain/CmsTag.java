package xyz.byqy.vane.content.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import xyz.byqy.vane.common.persistence.AuditableEntity;

/** cms_tag（05 §6）：文章标签；标签展示名直接使用 code。 */
@Entity
@Table(name = "cms_tag")
public class CmsTag extends AuditableEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String code;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected CmsTag() {
    }

    public static CmsTag create(UUID id, String code) {
        CmsTag tag = new CmsTag();
        tag.id = id;
        tag.code = code;
        tag.createdAt = Instant.now();
        return tag;
    }

    public void softDelete() {
        this.deletedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }
}
