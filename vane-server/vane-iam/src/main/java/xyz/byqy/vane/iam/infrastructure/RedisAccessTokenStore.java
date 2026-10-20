package xyz.byqy.vane.iam.infrastructure;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import xyz.byqy.vane.iam.application.AccessTokenStore;

/** Valkey/Redis 实现：以哈希为键，TTL 与 access 有效期一致。 */
@Component
@ConditionalOnProperty(prefix = "vane.auth", name = "token-store", havingValue = "redis", matchIfMissing = true)
public class RedisAccessTokenStore implements AccessTokenStore {

    private static final String PREFIX = "vane:at:";

    private final StringRedisTemplate redis;

    public RedisAccessTokenStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public void store(String tokenHash, UUID userId, Duration ttl) {
        redis.opsForValue().set(PREFIX + tokenHash, userId.toString(), ttl);
    }

    @Override
    public Optional<UUID> resolve(String tokenHash) {
        String value = redis.opsForValue().get(PREFIX + tokenHash);
        return value == null ? Optional.empty() : Optional.of(UUID.fromString(value));
    }

    @Override
    public void revoke(String tokenHash) {
        redis.delete(PREFIX + tokenHash);
    }
}
