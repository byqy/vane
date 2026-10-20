package xyz.byqy.vane.file.domain;

import xyz.byqy.vane.common.persistence.AuditableEntity;
import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** sys_file（05 §5.8）：object_key 不对外暴露，访问规则见 03 §15。 */
@Entity
@Table(name = "sys_file")
public class SysFile extends AuditableEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String storage;

    @Column(name = "object_key", nullable = false)
    private String objectKey;

    @Column(name = "original_name", nullable = false)
    private String originalName;

    @Column(nullable = false)
    private String mime;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false)
    private String sha256;

    @Column(nullable = false)
    private String visibility;

    @Column(name = "owner_user_id")
    private UUID ownerUserId;

    @Column(name = "biz_type")
    private String bizType;

    @Column(name = "biz_id")
    private UUID bizId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected SysFile() {
    }

    public SysFile(UUID id, String storage, String objectKey, String originalName, String mime,
                   long sizeBytes, String sha256, String visibility, UUID ownerUserId, Instant createdAt) {
        this.id = id;
        this.storage = storage;
        this.objectKey = objectKey;
        this.originalName = originalName;
        this.mime = mime;
        this.sizeBytes = sizeBytes;
        this.sha256 = sha256;
        this.visibility = visibility;
        this.ownerUserId = ownerUserId;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public String getStorage() {
        return storage;
    }

    public String getObjectKey() {
        return objectKey;
    }

    public String getOriginalName() {
        return originalName;
    }

    public String getMime() {
        return mime;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public String getSha256() {
        return sha256;
    }

    public String getVisibility() {
        return visibility;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public void softDelete() {
        this.deletedAt = Instant.now();
    }

    public boolean isPublic() {
        return "PUBLIC".equals(visibility);
    }
}



