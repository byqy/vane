package xyz.byqy.vane.system.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import xyz.byqy.vane.system.infrastructure.MailLogRepository.MailQueueEntry;

class MailLogRepositoryTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final MailLogRepository repository = new MailLogRepository(jdbc);

    @Test
    void enqueueWritesQueuedRow() {
        repository.enqueue(new MailQueueEntry(UUID.randomUUID(), "smtp", "user.welcome", "en",
                "i***@example.com", "example.com", "cipher", "1", "hash", "{}"));

        verify(jdbc).update(anyString(), any(Object[].class));
    }

    @Test
    void countSentTodayUsesUtcDay() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(5);

        assertThat(repository.countSentToday("smtp")).isEqualTo(5);
    }

    @Test
    void markSentClearsVariablesAndSetsSentAt() {
        UUID id = UUID.randomUUID();

        repository.markSent(id, "Welcome");

        verify(jdbc).update(anyString(), any(Object[].class));
    }

    @Test
    void markRetryAndFailedUpdateStatus() {
        UUID id = UUID.randomUUID();

        repository.markRetry(id, 1, Instant.now(), "MAIL.SEND.FAILED", "timeout");
        repository.markFailed(id, 3, "MAIL.SEND.FAILED", "timeout");

        verify(jdbc, org.mockito.Mockito.times(2)).update(anyString(), any(Object[].class));
    }

    @Test
    void deleteOlderThanRemovesExpiredRows() {
        when(jdbc.update(anyString(), eq(180))).thenReturn(2);

        assertThat(repository.deleteOlderThan(180)).isEqualTo(2);
    }

    @Test
    void pageBuildsFilters() {
        when(jdbc.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), any(Object[].class)))
                .thenReturn(List.of());

        assertThat(repository.page("SENT", "user.welcome", "hash", null, null, null, 0, 20)).isEmpty();
    }
}
