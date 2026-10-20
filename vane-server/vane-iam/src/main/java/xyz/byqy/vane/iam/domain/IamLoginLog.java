package xyz.byqy.vane.iam.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** iam_login_log（05 §4.8）：只增不改不删。 */
@Entity
@Table(name = "iam_login_log")
public class IamLoginLog {

    @Id
    private UUID id;

    @Column(name = "user_id")
    private UUID userId;

    @Column(nullable = false)
    private String username;

    @Column(nullable = false)
    private String result;

    private String detail;

    @JdbcTypeCode(SqlTypes.INET)
    private String ip;

    @Column(name = "user_agent")
    private String userAgent;

    @Column(name = "trace_id")
    private String traceId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected IamLoginLog() {
    }

    public static IamLoginLog create(UUID id, UUID userId, String username, String result, String detail,
                                     String ip, String userAgent, String traceId, Instant createdAt) {
        IamLoginLog log = new IamLoginLog();
        log.id = id;
        log.userId = userId;
        log.username = username;
        log.result = result;
        log.detail = detail;
        log.ip = ip;
        log.userAgent = userAgent;
        log.traceId = traceId;
        log.createdAt = createdAt;
        return log;
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

    public String getResult() {
        return result;
    }

    public String getDetail() {
        return detail;
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

