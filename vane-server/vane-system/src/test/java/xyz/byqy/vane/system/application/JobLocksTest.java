package xyz.byqy.vane.system.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

/** 任务锁在 Valkey 可用 / 被占用 / 不可用三种情况下的行为。 */
class JobLocksTest {

    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> valueOps = mock(ValueOperations.class);

    @Test
    void acquireSucceedsWhenLockIsFree() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);

        assertThat(JobLocks.tryAcquire(redis, "vane:test:lock:free", Duration.ofSeconds(30))).isTrue();
    }

    @Test
    void acquireReturnsFalseWhenAnotherInstanceHoldsLock() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(false);

        assertThat(JobLocks.tryAcquire(redis, "vane:test:lock:held", Duration.ofSeconds(30))).isFalse();
    }

    @Test
    void acquireSkipsTickWhenValkeyIsUnavailable() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenThrow(new RedisConnectionFailureException("Unable to connect to Redis"));

        // Valkey 不可用时不抛异常，返回 false 让任务本轮空转
        assertThat(JobLocks.tryAcquire(redis, "vane:test:lock:down", Duration.ofSeconds(30))).isFalse();
    }

    @Test
    void releaseSwallowsConnectionFailure() {
        when(redis.delete("vane:test:lock:release"))
                .thenThrow(new RedisConnectionFailureException("Unable to connect to Redis"));

        assertThatCode(() -> JobLocks.release(redis, "vane:test:lock:release"))
                .doesNotThrowAnyException();
        verify(redis).delete("vane:test:lock:release");
    }
}
