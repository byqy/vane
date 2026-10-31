package xyz.byqy.vane.system.application;

import java.time.Duration;
import java.util.Optional;

/**
 * 幂等键存储（ADR-030 执行层）。
 *
 * <p>保存窗口 24 小时（ADR-030 评审决议 2），实现落在 Valkey。</p>
 */
public interface IdempotencyStore {

    /** 查询键对应的记录。 */
    Optional<IdempotencyRecord> find(String scopeKey);

    /**
     * 原子地占用键：仅当键不存在时写入并返回 true。
     *
     * @return true 表示本次请求获得执行权；false 表示键已被其他请求占用
     */
    boolean tryStart(String scopeKey, IdempotencyRecord record, Duration ttl);

    /** 写入完成态记录（覆盖处理中记录）。 */
    void complete(String scopeKey, IdempotencyRecord record, Duration ttl);

    /** 释放键：用于服务端错误等未产生确定结果的情形，允许用同一键重试。 */
    void release(String scopeKey);
}
