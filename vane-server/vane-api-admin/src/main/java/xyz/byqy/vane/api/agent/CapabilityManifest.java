package xyz.byqy.vane.api.agent;

import java.util.List;

/**
 * 能力清单（ADR-030 能力层）：Agent 操作 vane 时的自描述入口。
 *
 * @param api          契约标识
 * @param operations   可用操作（方法、路径、权限码、副作用、幂等性、OpenAPI 锚点）
 * @param errorCatalog 错误码目录（含恢复指令）
 */
public record CapabilityManifest(String api, List<Operation> operations, List<ErrorEntry> errorCatalog) {

    /**
     * 一个可调用操作。
     *
     * @param method         HTTP 方法
     * @param path           路径模板
     * @param permissionCode 所需权限码（无鉴权要求时为 null）
     * @param permissions    全部所需权限码（任一满足即可时多于一个）
     * @param sideEffect     副作用：read / create / update / delete / action
     * @param idempotency    幂等性：SAFE 可直接重试、KEY 需带 Idempotency-Key
     * @param openApiRef     对应 OpenAPI 文档中的锚点，用于查请求与响应结构
     */
    public record Operation(String method, String path, String permissionCode, List<String> permissions,
                            String sideEffect, String idempotency, String openApiRef) {
    }

    /**
     * 一个错误码。
     *
     * @param code      错误码
     * @param status    HTTP 状态码
     * @param type      问题类型 URI
     * @param action    恢复指令
     * @param retryable 是否可原样重试
     */
    public record ErrorEntry(String code, int status, String type, String action, boolean retryable) {
    }
}
