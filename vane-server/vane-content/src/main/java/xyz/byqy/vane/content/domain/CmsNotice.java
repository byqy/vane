package xyz.byqy.vane.content.domain;

import xyz.byqy.vane.common.persistence.AuditableEntity;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** cms_notice（05 §6.2）：正文按语言存 cms_notice_i18n。 */
@Entity
@Table(name = "cms_notice")
public class CmsNotice extends AuditableEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String level;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private boolean pinned;

    @Column(name = "publish_at")
    private Instant publishAt;

    @Column(name = "expire_at")
    private Instant expireAt;

    @Column(name = "author_id")
    private UUID authorId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected CmsNotice() {
    }

    public static CmsNotice create(UUID id, String level, boolean pinned, Instant publishAt, Instant expireAt,
                                   UUID authorId) {
        CmsNotice notice = new CmsNotice();
        notice.id = id;
        notice.level = level;
        notice.pinned = pinned;
        notice.publishAt = publishAt;
        notice.expireAt = expireAt;
        notice.authorId = authorId;
        notice.status = "DRAFT";
        notice.createdAt = Instant.now();
        return notice;
    }

    public void update(String level, boolean pinned, Instant publishAt, Instant expireAt) {
        this.level = level;
        this.pinned = pinned;
        this.publishAt = publishAt;
        this.expireAt = expireAt;
    }

    public void publish(Instant now) {
        this.status = "PUBLISHED";
        if (this.publishAt == null) {
            this.publishAt = now;
        }
    }

    /**
     * 立即发布：不写 publish_at，由公开查询的「publish_at is null 视为立即生效」判定。
     * 避免应用时钟与数据库时钟存在毫秒级偏差时，刚发布的公告查不到。
     */
    public void publishImmediately() {
        this.status = "PUBLISHED";
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

    public String getLevel() {
        return level;
    }

    public String getStatus() {
        return status;
    }

    public boolean isPinned() {
        return pinned;
    }

    public Instant getPublishAt() {
        return publishAt;
    }

    public Instant getExpireAt() {
        return expireAt;
    }
}


