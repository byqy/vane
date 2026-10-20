package xyz.byqy.vane.iam.domain;

import xyz.byqy.vane.common.persistence.AuditableEntity;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** iam_dept（05 §4.1）。 */
@Entity
@Table(name = "iam_dept")
public class IamDept extends AuditableEntity {

    @Id
    private UUID id;

    @Column(name = "parent_id")
    private UUID parentId;

    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String status;

    @Column(name = "sort_no", nullable = false)
    private int sortNo;

    @Column(name = "deleted_at")
    private java.time.Instant deletedAt;

    protected IamDept() {
    }

    public static IamDept create(UUID id, UUID parentId, String code, String name, int sortNo) {
        IamDept dept = new IamDept();
        dept.id = id;
        dept.parentId = parentId;
        dept.code = code;
        dept.name = name;
        dept.sortNo = sortNo;
        dept.status = "ACTIVE";
        return dept;
    }

    public void update(UUID parentId, String name, String status, int sortNo) {
        this.parentId = parentId;
        if (name != null) {
            this.name = name;
        }
        if (status != null) {
            this.status = status;
        }
        this.sortNo = sortNo;
    }

    public void softDelete() {
        this.deletedAt = java.time.Instant.now();
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public int getSortNo() {
        return sortNo;
    }

    public String getStatus() {
        return status;
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

    public String getName() {
        return name;
    }
}


