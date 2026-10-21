package xyz.byqy.vane.audit.domain;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** sys_oper_log（05 §5.6）：只增不改不删；仅 http_status 由拦截器回填。 */
@Entity
@Table(name = "sys_oper_log")
public class SysOperLog {

    @Id
    private UUID id;

    @Column(name = "user_id")
    private UUID userId;

    private String username;

    @Column(nullable = false)
    private String module;

    @Column(nullable = false)
    private String action;

    @Column(nullable = false)
    private String source;

    @Column(name = "target_type")
    private String targetType;

    @Column(name = "target_id")
    private String targetId;

    @Column(name = "target_label")
    private String targetLabel;

    @Column(name = "http_method")
    private String httpMethod;

    private String path;

    @Column(name = "http_status")
    private Short httpStatus;

    @Column(name = "user_agent")
    private String userAgent;

    @Column(name = "dept_id")
    private UUID deptId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "params")
    private String params;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "changes")
    private String changes;

    @Column(name = "changes_version", nullable = false)
    private Short changesVersion;

    @Column(name = "changes_truncated", nullable = false)
    private boolean changesTruncated;

    @Column(name = "affected_count")
    private Integer affectedCount;

    @Column(nullable = false)
    private String result;

    @Column(name = "error_code")
    private String errorCode;

    @Column(name = "duration_ms")
    private Integer durationMs;

    @JdbcTypeCode(SqlTypes.INET)
    private String ip;

    @Column(name = "trace_id")
    private String traceId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected SysOperLog() {
    }

    public SysOperLog(UUID id, UUID userId, String username, String module, String action, String source,
                      String targetType, String targetId, String targetLabel, String httpMethod, String path,
                      Short httpStatus, String userAgent, UUID deptId, String params, String changes,
                      Short changesVersion, boolean changesTruncated, Integer affectedCount, String result,
                      String errorCode, Integer durationMs, String ip, String traceId, Instant createdAt) {
        this.id = id;
        this.userId = userId;
        this.username = username;
        this.module = module;
        this.action = action;
        this.source = source;
        this.targetType = targetType;
        this.targetId = targetId;
        this.targetLabel = targetLabel;
        this.httpMethod = httpMethod;
        this.path = path;
        this.httpStatus = httpStatus;
        this.userAgent = userAgent;
        this.deptId = deptId;
        this.params = params;
        this.changes = changes;
        this.changesVersion = changesVersion;
        this.changesTruncated = changesTruncated;
        this.affectedCount = affectedCount;
        this.result = result;
        this.errorCode = errorCode;
        this.durationMs = durationMs;
        this.ip = ip;
        this.traceId = traceId;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public String getModule() {
        return module;
    }

    public String getAction() {
        return action;
    }

    public String getSource() {
        return source;
    }

    public String getTargetType() {
        return targetType;
    }

    public String getTargetId() {
        return targetId;
    }

    public String getTargetLabel() {
        return targetLabel;
    }

    public String getHttpMethod() {
        return httpMethod;
    }

    public String getPath() {
        return path;
    }

    public Short getHttpStatus() {
        return httpStatus;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public UUID getDeptId() {
        return deptId;
    }

    public String getParams() {
        return params;
    }

    public String getChanges() {
        return changes;
    }

    public Short getChangesVersion() {
        return changesVersion;
    }

    public boolean isChangesTruncated() {
        return changesTruncated;
    }

    public Integer getAffectedCount() {
        return affectedCount;
    }

    public String getResult() {
        return result;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public Integer getDurationMs() {
        return durationMs;
    }

    public String getIp() {
        return ip;
    }

    public String getTraceId() {
        return traceId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
