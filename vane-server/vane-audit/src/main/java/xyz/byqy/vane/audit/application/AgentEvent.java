package xyz.byqy.vane.audit.application;

import java.time.Instant;

/**
 * 审计事件的只读投影（ADR-030 P1）：面向代理的「谁在什么时候做了什么」。
 *
 * <p>只暴露排障需要的字段，不含请求参数与字段级变更，避免代理看到敏感数据。</p>
 */
public record AgentEvent(String id, Instant at, String actor, String module, String action,
                         String targetType, String targetId, String targetLabel,
                         String httpMethod, String path, Integer status, String result,
                         String errorCode, String traceId) {
}
