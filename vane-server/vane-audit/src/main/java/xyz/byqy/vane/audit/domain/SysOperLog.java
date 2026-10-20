package xyz.byqy.vane.audit.domain;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** sys_oper_log（05 §5.6）：只增不改不删。 */
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

    @Column(name = "http_method")
    private String httpMethod;

    private String path;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "params")
    private String params;

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

    public SysOperLog(UUID id, UUID userId, String username, String module, String action,
                      String httpMethod, String path, String params, String result, String errorCode,
                      Integer durationMs, String ip, String traceId, Instant createdAt) {
        this.id = id;
        this.userId = userId;
        this.username = username;
        this.module = module;
        this.action = action;
        this.httpMethod = httpMethod;
        this.path = path;
        this.params = params;
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

    public String getUsername() {
        return username;
    }

    public String getModule() {
        return module;
    }

    public String getAction() {
        return action;
    }

    public String getHttpMethod() {
        return httpMethod;
    }

    public String getParams() {
        return params;
    }

    public String getPath() {
        return path;
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
