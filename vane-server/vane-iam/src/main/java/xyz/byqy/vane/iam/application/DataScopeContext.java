package xyz.byqy.vane.iam.application;

import java.util.Set;
import java.util.UUID;

/**
 * 数据范围上下文：查询层据此注入过滤条件。
 * all=true 表示不受限；否则可见集合为 deptIds ∪ (selfOnly ? 本人 : 空)。
 */
public record DataScopeContext(boolean all, Set<UUID> deptIds, boolean selfOnly, UUID userId) {

    public static DataScopeContext unrestricted(UUID userId) {
        return new DataScopeContext(true, Set.of(), false, userId);
    }

    public static DataScopeContext selfOnly(UUID userId) {
        return new DataScopeContext(false, Set.of(), true, userId);
    }
}
