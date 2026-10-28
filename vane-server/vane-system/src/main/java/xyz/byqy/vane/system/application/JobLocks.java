package xyz.byqy.vane.system.application;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 定时任务的分布式锁（Valkey）。
 *
 * <p>Valkey 不可用时（容器未启动、网络中断、维护窗口）**跳过本轮**并限频告警，
 * 而不是让任务失败、在日志里刷整段连接异常堆栈：
 * 调度器仍按 cron 触发，Valkey 恢复后自动继续工作。</p>
 *
 * <p>限频说明：同一个锁 key 最多每 {@link #WARN_INTERVAL} 告警一次，
 * 避免每分钟一条 WARN 淹没日志。</p>
 */
final class JobLocks {

    private static final Logger log = LoggerFactory.getLogger(JobLocks.class);
    private static final Duration WARN_INTERVAL = Duration.ofMinutes(5);
    private static final Map<String, Instant> WARNED_AT = new ConcurrentHashMap<>();

    private JobLocks() {
    }

    /**
     * 尝试加锁。
     *
     * @return true 表示本轮拿到锁；false 表示已被其他实例持有，或 Valkey 不可用
     */
    static boolean tryAcquire(StringRedisTemplate redis, String key, Duration ttl) {
        try {
            return Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key, UUID.randomUUID().toString(), ttl));
        } catch (DataAccessException e) {
            warnUnavailable(key, e);
            return false;
        }
    }

    /** 释放锁；失败只告警，不影响任务结果。 */
    static void release(StringRedisTemplate redis, String key) {
        try {
            redis.delete(key);
        } catch (DataAccessException e) {
            warnUnavailable(key, e);
        }
    }

    private static void warnUnavailable(String key, DataAccessException e) {
        Instant now = Instant.now();
        Instant last = WARNED_AT.get(key);
        if (last != null && last.plus(WARN_INTERVAL).isAfter(now)) {
            return;
        }
        WARNED_AT.put(key, now);
        log.warn("Valkey 不可用，跳过本轮任务（锁 {}）：{}", key, e.getMessage());
    }
}
