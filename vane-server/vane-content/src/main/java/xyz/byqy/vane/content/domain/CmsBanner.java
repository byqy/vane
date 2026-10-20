package xyz.byqy.vane.content.domain;

import xyz.byqy.vane.common.persistence.AuditableEntity;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** cms_banner（05 §6.4）：首页轮播与宣传位。 */
@Entity
@Table(name = "cms_banner")
public class CmsBanner extends AuditableEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String position;

    @Column(name = "image_file_id", nullable = false)
    private UUID imageFileId;

    @Column(name = "title_key")
    private String titleKey;

    @Column(name = "link_url")
    private String linkUrl;

    @Column(name = "sort_no", nullable = false)
    private int sortNo;

    @Column(nullable = false)
    private String status;

    @Column(name = "start_at")
    private Instant startAt;

    @Column(name = "end_at")
    private Instant endAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected CmsBanner() {
    }

    public static CmsBanner create(UUID id, String position, UUID imageFileId, String titleKey, String linkUrl,
                                   int sortNo, Instant startAt, Instant endAt) {
        CmsBanner banner = new CmsBanner();
        banner.id = id;
        banner.position = position;
        banner.imageFileId = imageFileId;
        banner.titleKey = titleKey;
        banner.linkUrl = linkUrl;
        banner.sortNo = sortNo;
        banner.status = "ACTIVE";
        banner.startAt = startAt;
        banner.endAt = endAt;
        banner.createdAt = Instant.now();
        return banner;
    }

    public void update(UUID imageFileId, String titleKey, String linkUrl, int sortNo, String status,
                       Instant startAt, Instant endAt) {
        this.imageFileId = imageFileId;
        this.titleKey = titleKey;
        this.linkUrl = linkUrl;
        this.sortNo = sortNo;
        if (status != null) {
            this.status = status;
        }
        this.startAt = startAt;
        this.endAt = endAt;
    }

    public void softDelete() {
        this.deletedAt = Instant.now();
    }

    public boolean isActiveAt(Instant now) {
        return "ACTIVE".equals(status)
                && (startAt == null || !startAt.isAfter(now))
                && (endAt == null || endAt.isAfter(now));
    }

    public UUID getId() {
        return id;
    }

    public String getPosition() {
        return position;
    }

    public UUID getImageFileId() {
        return imageFileId;
    }

    public String getTitleKey() {
        return titleKey;
    }

    public String getLinkUrl() {
        return linkUrl;
    }

    public int getSortNo() {
        return sortNo;
    }

    public String getStatus() {
        return status;
    }

    public Instant getStartAt() {
        return startAt;
    }

    public Instant getEndAt() {
        return endAt;
    }
}

