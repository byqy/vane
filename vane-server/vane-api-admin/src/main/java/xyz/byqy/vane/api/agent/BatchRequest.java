package xyz.byqy.vane.api.agent;

import java.util.List;
import java.util.Map;

/**
 * 批量请求（ADR-030 P1）：一次提交多个创建/修改操作，逐项返回结果。
 *
 * @param operations 操作列表，最多 20 项，按顺序串行执行
 */
public record BatchRequest(List<Item> operations) {

    /**
     * 单个操作。
     *
     * @param id      调用方自定的标识，用于把结果与请求对应起来
     * @param method  HTTP 方法，限 POST / PATCH / PUT / DELETE
     * @param path    以 `/api/v1/` 开头，且不得指向 `/api/v1/agent/`
     * @param body    请求体（JSON）
     * @param headers 额外请求头，例如 `Idempotency-Key`
     */
    public record Item(String id, String method, String path, Object body, Map<String, String> headers) {
    }
}
