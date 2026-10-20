package xyz.byqy.vane.iam.infrastructure;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import xyz.byqy.vane.iam.application.MfaChallengeStore;

@Component
@ConditionalOnProperty(prefix = "vane.auth", name = "token-store", havingValue = "redis", matchIfMissing = true)
public class RedisMfaChallengeStore implements MfaChallengeStore {

    private static final String PREFIX = "vane:mfa:";

    private final StringRedisTemplate redis;

    public RedisMfaChallengeStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public void store(String tokenHash, UUID userId, Duration ttl) {
        redis.opsForValue().set(PREFIX + tokenHash, userId.toString(), ttl);
    }

    @Override
    public Optional<UUID> consume(String tokenHash) {
        String value = redis.opsForValue().getAndDelete(PREFIX + tokenHash);
        return value == null ? Optional.empty() : Optional.of(UUID.fromString(value));
    }
}
