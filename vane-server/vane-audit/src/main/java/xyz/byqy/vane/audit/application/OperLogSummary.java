package xyz.byqy.vane.audit.application;

import java.time.Instant;
import java.util.UUID;

public record OperLogSummary(UUID id, String username, String module, String action, String source,
                             String targetType, String targetId, String targetLabel, String httpMethod,
                             String path, Integer httpStatus, String result, String errorCode,
                             Integer durationMs, Integer affectedCount, String ip, String userAgent, UUID deptId,
                             String traceId, Instant createdAt) {
}
