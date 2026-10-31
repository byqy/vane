package xyz.byqy.vane.system.application;

/**
 * 高危操作的二次确认令牌（ADR-030 P2）。
 *
 * @param id     令牌 id（即客户端回传的值）
 * @param actor  申请确认的账号
 * @param method 被确认的 HTTP 方法
 * @param path   被确认的具体路径
 */
public record ConfirmationToken(String id, String actor, String method, String path) {
}
