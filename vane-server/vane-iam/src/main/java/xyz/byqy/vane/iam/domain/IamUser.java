package xyz.byqy.vane.iam.domain;

import xyz.byqy.vane.common.persistence.AuditableEntity;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** iam_user（05 §4.2）。注销使用 deleted_at 软删除。 */
@Entity
@Table(name = "iam_user")
public class IamUser extends AuditableEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String username;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    private String nickname;

    @Column(name = "dept_id", nullable = false)
    private UUID deptId;

    @Column(nullable = false)
    private String status;

    @Column(name = "mfa_enabled", nullable = false)
    private boolean mfaEnabled;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "password_updated_at")
    private Instant passwordUpdatedAt;

    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword;

    private String locale;

    private String timezone;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected IamUser() {
    }

    /** 创建用户（供初始化与管理端使用）。 */
    public static IamUser create(UUID id, String username, String passwordHash, UUID deptId, String status) {
        IamUser user = new IamUser();
        user.id = id;
        user.username = username;
        user.passwordHash = passwordHash;
        user.deptId = deptId;
        user.status = status;
        user.mustChangePassword = true;
        Instant now = Instant.now();
        user.createdAt = now;
        user.updatedAt = now;
        return user;
    }

    /** 可登录：状态 ACTIVE 且未删除。 */
    public boolean isActive() {
        return "ACTIVE".equals(status) && deletedAt == null;
    }

    /** 记录存在：仅要求未删除。管理端查看与编辑不因禁用而 404。 */
    public boolean isNotDeleted() {
        return deletedAt == null;
    }

    public void updateProfile(String nickname, UUID deptId, String status) {
        this.nickname = nickname;
        if (deptId != null) {
            this.deptId = deptId;
        }
        if (status != null) {
            this.status = status;
        }
        this.updatedAt = Instant.now();
    }

    public void softDelete() {
        this.deletedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void changePassword(String newHash) {
        this.passwordHash = newHash;
        this.passwordUpdatedAt = Instant.now();
        this.mustChangePassword = true;
        this.updatedAt = Instant.now();
    }

    public void enableMfa() {
        this.mfaEnabled = true;
        this.updatedAt = Instant.now();
    }

    public void disableMfa() {
        this.mfaEnabled = false;
        this.updatedAt = Instant.now();
    }

    public void lockUntil(Instant until) {
        this.lockedUntil = until;
        this.updatedAt = Instant.now();
    }

    public void clearLock() {
        this.lockedUntil = null;
    }

    public void markLoggedIn(Instant when) {
        this.lastLoginAt = when;
        this.updatedAt = when;
    }

    public UUID getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public long getVersion() {
        return version;
    }

    public String getNickname() {
        return nickname;
    }

    public UUID getDeptId() {
        return deptId;
    }

    public String getStatus() {
        return status;
    }

    public boolean isMfaEnabled() {
        return mfaEnabled;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }

    /** 本人修改密码：清除强制改密标记。 */
    public void changePasswordBySelf(String newHash) {
        this.passwordHash = newHash;
        this.passwordUpdatedAt = Instant.now();
        this.mustChangePassword = false;
        this.updatedAt = Instant.now();
    }

    public boolean isMustChangePassword() {
        return mustChangePassword;
    }

    /** 更新界面语言偏好（BCP-47 标签，如 zh-CN / en）。 */
    public void changeLocale(String locale) {
        this.locale = locale;
        this.updatedAt = Instant.now();
    }

    /** 更新用户时区（IANA ID，如 Asia/Shanghai），供异步场景格式化时间。 */
    public void changeTimezone(String timezone) {
        this.timezone = timezone;
        this.updatedAt = Instant.now();
    }

    public String getLocale() {
        return locale;
    }

    public String getTimezone() {
        return timezone;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }
}












