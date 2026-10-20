package xyz.byqy.vane.iam.domain;

import xyz.byqy.vane.common.persistence.AuditableEntity;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** iam_role（05 §4.3）。 */
@Entity
@Table(name = "iam_role")
public class IamRole extends AuditableEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(name = "data_scope", nullable = false)
    private String dataScope;

    @Column(nullable = false)
    private boolean builtin;

    @Column(nullable = false)
    private String status;

    @Column(name = "sort_no", nullable = false)
    private int sortNo;

    @Column(name = "deleted_at")
    private java.time.Instant deletedAt;

    protected IamRole() {
    }

    public static IamRole create(UUID id, String code, String name, String dataScope) {
        IamRole role = new IamRole();
        role.id = id;
        role.code = code;
        role.name = name;
        role.dataScope = dataScope;
        role.builtin = false;
        role.status = "ACTIVE";
        return role;
    }

    public void update(String name, String dataScope, String status, int sortNo) {
        if (name != null) {
            this.name = name;
        }
        if (dataScope != null) {
            this.dataScope = dataScope;
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

    public UUID getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getDataScope() {
        return dataScope;
    }

    public boolean isBuiltin() {
        return builtin;
    }

    public int getSortNo() {
        return sortNo;
    }

    public String getStatus() {
        return status;
    }
}



