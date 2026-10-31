package xyz.byqy.vane.system.infrastructure;

import java.time.Duration;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import tools.jackson.databind.ObjectMapper;

import xyz.byqy.vane.system.application.IdempotencyRecord;
import xyz.byqy.vane.system.application.IdempotencyStore;

/** 幂等键的 Valkey 实现：键带 TTL，超时自动清理。 */
@Component
public class RedisIdempotencyStore implements IdempotencyStore {

    private static final Logger log = LoggerFactory.getLogger(RedisIdempotencyStore.class);
    private static final String PREFIX = "vane:idempotency:";

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;

    public RedisIdempotencyStore(StringRedisTemplate redis, ObjectMapper mapper) {
        this.redis = redis;
        this.mapper = mapper;
    }

    @Override
    public Optional<IdempotencyRecord> find(String scopeKey) {
        String raw = redis.opsForValue().get(PREFIX + scopeKey);
        if (raw == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(mapper.readValue(raw, IdempotencyRecord.class));
        } catch (RuntimeException e) {
            // 记录损坏时按「无记录」处理，避免永久卡住请求
            log.warn("幂等记录解析失败，已忽略：key={}", scopeKey, e);
            return Optional.empty();
        }
    }

    @Override
    public boolean tryStart(String scopeKey, IdempotencyRecord record, Duration ttl) {
        Boolean acquired = redis.opsForValue().setIfAbsent(PREFIX + scopeKey, mapper.writeValueAsString(record), ttl);
        return Boolean.TRUE.equals(acquired);
    }

    @Override
    public void complete(String scopeKey, IdempotencyRecord record, Duration ttl) {
        redis.opsForValue().set(PREFIX + scopeKey, mapper.writeValueAsString(record), ttl);
    }

    @Override
    public void release(String scopeKey) {
        redis.delete(PREFIX + scopeKey);
    }
}
