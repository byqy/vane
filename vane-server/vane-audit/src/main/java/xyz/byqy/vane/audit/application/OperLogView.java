package xyz.byqy.vane.audit.application;

import java.time.Instant;
import java.util.UUID;

public record OperLogView(UUID id, String username, String module, String action, String httpMethod, String path,
                          String params, String result, String errorCode, Integer durationMs, String ip,
                          String traceId, Instant createdAt) {
}

