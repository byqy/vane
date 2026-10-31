package xyz.byqy.vane.api.agent;

import java.util.List;

/**
 * 批量结果（ADR-030 P1）：逐项结果，不让一个失败吞掉整批。
 *
 * @param total     提交的操作数
 * @param succeeded 成功数（2xx）
 * @param failed    失败数
 * @param results   逐项结果，顺序与请求一致
 */
public record BatchResult(int total, int succeeded, int failed, List<ItemResult> results) {

    /**
     * 单个操作的结果。
     *
     * @param id       对应请求里的 {@code id}
     * @param status   HTTP 状态码
     * @param body     响应体（失败时为 Problem Details）
     * @param action   失败时的恢复指令，直接取自 Problem Details
     * @param replayed 是否为幂等重放
     */
    public record ItemResult(String id, int status, Object body, String action, boolean replayed) {
    }
}
