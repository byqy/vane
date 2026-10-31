package xyz.byqy.vane.system.infrastructure;

import java.time.Duration;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import tools.jackson.databind.ObjectMapper;

import xyz.byqy.vane.system.application.ConfirmationStore;
import xyz.byqy.vane.system.application.ConfirmationToken;

/** 二次确认令牌的 Valkey 实现：带 TTL，消费即删除。 */
@Component
public class RedisConfirmationStore implements ConfirmationStore {

    private static final Logger log = LoggerFactory.getLogger(RedisConfirmationStore.class);
    private static final String PREFIX = "vane:confirm:";

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;

    public RedisConfirmationStore(StringRedisTemplate redis, ObjectMapper mapper) {
        this.redis = redis;
        this.mapper = mapper;
    }

    @Override
    public void save(ConfirmationToken token, Duration ttl) {
        redis.opsForValue().set(PREFIX + token.id(), mapper.writeValueAsString(token), ttl);
    }

    @Override
    public Optional<ConfirmationToken> find(String id) {
        String raw = redis.opsForValue().get(PREFIX + id);
        if (raw == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(mapper.readValue(raw, ConfirmationToken.class));
        } catch (RuntimeException e) {
            log.warn("确认令牌解析失败，已忽略：id={}", id, e);
            return Optional.empty();
        }
    }

    @Override
    public boolean consume(String id) {
        return Boolean.TRUE.equals(redis.delete(PREFIX + id));
    }
}
