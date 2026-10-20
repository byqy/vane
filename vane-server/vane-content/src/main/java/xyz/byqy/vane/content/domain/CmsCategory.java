package xyz.byqy.vane.content.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import xyz.byqy.vane.common.persistence.AuditableEntity;

/**
 * cms_category（05 §6）：文章分类。
 *
 * <p>业务上只支持一级分类：{@code parent_id} 字段保留以兼容未来多级，但当前实现只写 null，
 * 也不提供多级树操作。</p>
 */
@Entity
@Table(name = "cms_category")
public class CmsCategory extends AuditableEntity {

    @Id
    private UUID id;

    @Column(name = "parent_id")
    private UUID parentId;

    @Column(nullable = false)
    private String code;

    @Column(name = "sort_no", nullable = false)
    private int sortNo;

    @Column(nullable = false)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected CmsCategory() {
    }

    public static CmsCategory create(UUID id, String code, int sortNo, String status) {
        CmsCategory category = new CmsCategory();
        category.id = id;
        category.parentId = null;
        category.code = code;
        category.sortNo = sortNo;
        category.status = status;
        category.createdAt = Instant.now();
        return category;
    }

    public void update(String code, int sortNo, String status) {
        this.code = code;
        this.sortNo = sortNo;
        this.status = status;
    }

    public void softDelete() {
        this.deletedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getParentId() {
        return parentId;
    }

    public String getCode() {
        return code;
    }

    public int getSortNo() {
        return sortNo;
    }

    public String getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }
}
