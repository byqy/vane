package xyz.byqy.vane.iam.infrastructure;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import xyz.byqy.vane.iam.application.MfaChallengeStore;

@Component
@ConditionalOnProperty(prefix = "vane.auth", name = "token-store", havingValue = "memory")
public class InMemoryMfaChallengeStore implements MfaChallengeStore {

    private record Entry(UUID userId, Instant expiresAt) {
    }

    private final ConcurrentHashMap<String, Entry> entries = new ConcurrentHashMap<>();

    @Override
    public void store(String tokenHash, UUID userId, Duration ttl) {
        entries.put(tokenHash, new Entry(userId, Instant.now().plus(ttl)));
    }

    @Override
    public Optional<UUID> consume(String tokenHash) {
        Entry entry = entries.remove(tokenHash);
        if (entry == null || entry.expiresAt().isBefore(Instant.now())) {
            return Optional.empty();
        }
        return Optional.of(entry.userId());
    }
}
