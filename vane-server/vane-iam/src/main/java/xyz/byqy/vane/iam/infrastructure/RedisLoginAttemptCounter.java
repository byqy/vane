package xyz.byqy.vane.iam.infrastructure;

import java.time.Duration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import xyz.byqy.vane.iam.application.LoginAttemptCounter;

/** Valkey 实现：INCR + EXPIRE，天然并发安全。 */
@Component
@ConditionalOnProperty(prefix = "vane.auth", name = "token-store", havingValue = "redis", matchIfMissing = true)
public class RedisLoginAttemptCounter implements LoginAttemptCounter {

    private final StringRedisTemplate redis;

    public RedisLoginAttemptCounter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public long increment(String key, Duration ttl) {
        Long value = redis.opsForValue().increment(key);
        long count = value == null ? 1L : value;
        if (count == 1L) {
            redis.expire(key, ttl);
        }
        return count;
    }

    @Override
    public long count(String key) {
        String value = redis.opsForValue().get(key);
        return value == null ? 0L : Long.parseLong(value);
    }

    @Override
    public void clear(String key) {
        redis.delete(key);
    }
}
