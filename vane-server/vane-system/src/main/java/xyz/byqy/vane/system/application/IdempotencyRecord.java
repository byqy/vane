package xyz.byqy.vane.system.application;

/**
 * 幂等记录（ADR-030 执行层）。
 *
 * @param fingerprint  请求指纹（方法、路径、操作者与请求体的摘要），用于识别「同键不同请求」
 * @param state        状态：{@link #STATE_IN_FLIGHT} 处理中、{@link #STATE_DONE} 已完成
 * @param status       已完成时的 HTTP 状态码
 * @param contentType  已完成时的响应媒体类型
 * @param location     已完成时的 Location 头
 * @param body         已完成时的响应体（UTF-8 文本）
 */
public record IdempotencyRecord(String fingerprint, String state, Integer status, String contentType,
                                String location, String body) {

    /** 处理中：同一键的并发请求应等待或改用新键。 */
    public static final String STATE_IN_FLIGHT = "IN_FLIGHT";

    /** 已完成：同一键的重放直接返回首次响应。 */
    public static final String STATE_DONE = "DONE";

    public static IdempotencyRecord inFlight(String fingerprint) {
        return new IdempotencyRecord(fingerprint, STATE_IN_FLIGHT, null, null, null, null);
    }

    public static IdempotencyRecord done(String fingerprint, int status, String contentType, String location, String body) {
        return new IdempotencyRecord(fingerprint, STATE_DONE, status, contentType, location, body);
    }
}
