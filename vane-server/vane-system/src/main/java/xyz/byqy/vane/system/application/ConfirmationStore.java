package xyz.byqy.vane.system.application;

import java.time.Duration;
import java.util.Optional;

/**
 * 二次确认令牌存储（ADR-030 P2）。
 *
 * <p>令牌一次性使用、限时（默认 5 分钟）、绑定「操作者 + 方法 + 路径」，
 * 由 {@code POST /api/v1/agent/confirmations} 签发，高危操作请求时回传。</p>
 */
public interface ConfirmationStore {

    void save(ConfirmationToken token, Duration ttl);

    /** 取出但**不删除**，由调用方校验通过后再 {@link #consume}。 */
    Optional<ConfirmationToken> find(String id);

    /** 原子消费：仅当令牌仍存在时删除并返回 true。 */
    boolean consume(String id);
}
