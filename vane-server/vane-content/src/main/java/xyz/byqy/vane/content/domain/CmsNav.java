package xyz.byqy.vane.content.domain;

import xyz.byqy.vane.common.persistence.AuditableEntity;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** cms_nav（05 §6.3）：文案用 i18n key，不入库。 */
@Entity
@Table(name = "cms_nav")
public class CmsNav extends AuditableEntity {

    @Id
    private UUID id;

    @Column(name = "parent_id")
    private UUID parentId;

    @Column(nullable = false)
    private String position;

    @Column(name = "label_key", nullable = false)
    private String labelKey;

    private String target;

    @Column(name = "open_in_new_tab", nullable = false)
    private boolean openInNewTab;

    @Column(name = "sort_no", nullable = false)
    private int sortNo;

    @Column(nullable = false)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected CmsNav() {
    }

    public static CmsNav create(UUID id, UUID parentId, String position, String labelKey, String target,
                                boolean openInNewTab, int sortNo) {
        CmsNav nav = new CmsNav();
        nav.id = id;
        nav.parentId = parentId;
        nav.position = position;
        nav.labelKey = labelKey;
        nav.target = target;
        nav.openInNewTab = openInNewTab;
        nav.sortNo = sortNo;
        nav.status = "ACTIVE";
        nav.createdAt = Instant.now();
        return nav;
    }

    public void update(UUID parentId, String labelKey, String target, boolean openInNewTab, int sortNo, String status) {
        this.parentId = parentId;
        this.labelKey = labelKey;
        this.target = target;
        this.openInNewTab = openInNewTab;
        this.sortNo = sortNo;
        if (status != null) {
            this.status = status;
        }
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

    public String getPosition() {
        return position;
    }

    public String getLabelKey() {
        return labelKey;
    }

    public String getTarget() {
        return target;
    }

    public boolean isOpenInNewTab() {
        return openInNewTab;
    }

    public int getSortNo() {
        return sortNo;
    }

    public String getStatus() {
        return status;
    }
}

