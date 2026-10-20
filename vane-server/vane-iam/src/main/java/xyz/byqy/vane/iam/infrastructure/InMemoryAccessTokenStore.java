package xyz.byqy.vane.iam.infrastructure;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import xyz.byqy.vane.iam.application.AccessTokenStore;

/** 单实例/测试用的进程内实现；生产使用 Redis（Valkey）。 */
@Component
@ConditionalOnProperty(prefix = "vane.auth", name = "token-store", havingValue = "memory")
public class InMemoryAccessTokenStore implements AccessTokenStore {

    private record Entry(UUID userId, Instant expiresAt) {
    }

    private final ConcurrentHashMap<String, Entry> tokens = new ConcurrentHashMap<>();

    @Override
    public void store(String tokenHash, UUID userId, Duration ttl) {
        tokens.put(tokenHash, new Entry(userId, Instant.now().plus(ttl)));
    }

    @Override
    public Optional<UUID> resolve(String tokenHash) {
        Entry entry = tokens.get(tokenHash);
        if (entry == null) {
            return Optional.empty();
        }
        if (entry.expiresAt().isBefore(Instant.now())) {
            tokens.remove(tokenHash);
            return Optional.empty();
        }
        return Optional.of(entry.userId());
    }

    @Override
    public void revoke(String tokenHash) {
        tokens.remove(tokenHash);
    }
}
