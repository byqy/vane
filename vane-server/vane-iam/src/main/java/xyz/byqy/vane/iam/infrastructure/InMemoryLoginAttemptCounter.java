package xyz.byqy.vane.iam.infrastructure;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import xyz.byqy.vane.iam.application.LoginAttemptCounter;

@Component
@ConditionalOnProperty(prefix = "vane.auth", name = "token-store", havingValue = "memory")
public class InMemoryLoginAttemptCounter implements LoginAttemptCounter {

    private record Entry(long count, Instant expiresAt) {
    }

    private final ConcurrentHashMap<String, Entry> counters = new ConcurrentHashMap<>();

    @Override
    public long increment(String key, Duration ttl) {
        Instant now = Instant.now();
        Entry updated = counters.compute(key, (k, old) -> {
            if (old == null || old.expiresAt().isBefore(now)) {
                return new Entry(1, now.plus(ttl));
            }
            return new Entry(old.count() + 1, old.expiresAt());
        });
        return updated.count();
    }

    @Override
    public long count(String key) {
        Entry entry = counters.get(key);
        if (entry == null || entry.expiresAt().isBefore(Instant.now())) {
            counters.remove(key);
            return 0;
        }
        return entry.count();
    }

    @Override
    public void clear(String key) {
        counters.remove(key);
    }
}
