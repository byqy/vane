package xyz.byqy.vane.system.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import xyz.byqy.vane.system.infrastructure.SmsCampaignRepository;
import xyz.byqy.vane.system.infrastructure.SmsCampaignRepository.ProviderCount;

class SmsStatsServiceTest {

    private final SmsCampaignRepository campaigns = mock(SmsCampaignRepository.class);
    private final SmsStatsService service = new SmsStatsService(campaigns, new BigDecimal("0.05"));

    @Test
    void aggregatesSentFailedAndEstimatedCost() {
        Instant from = Instant.parse("2026-10-01T00:00:00Z");
        Instant to = Instant.parse("2026-10-20T00:00:00Z");
        when(campaigns.stats(from, to)).thenReturn(List.of(
                new ProviderCount("aliyun-cn", "SUCCESS", 10),
                new ProviderCount("aliyun-cn", "FAIL", 2),
                new ProviderCount("tencent-sea", "SUCCESS", 4)));

        SmsStatsService.Overview overview = service.overview(from, to, Instant.now());

        assertThat(overview.total()).isEqualTo(16);
        assertThat(overview.sent()).isEqualTo(14);
        assertThat(overview.failed()).isEqualTo(2);
        assertThat(overview.estimatedCost()).isEqualByComparingTo("0.70");
        assertThat(overview.byProvider()).hasSize(2);
    }

    @Test
    void defaultsToLast30DaysWhenRangeMissing() {
        Instant now = Instant.parse("2026-10-20T00:00:00Z");
        Instant expectedFrom = now.minusSeconds(30L * 24 * 3600);
        when(campaigns.stats(expectedFrom, now)).thenReturn(List.of());

        SmsStatsService.Overview overview = service.overview(null, null, now);

        assertThat(overview.from()).isEqualTo(expectedFrom);
        assertThat(overview.to()).isEqualTo(now);
        assertThat(overview.estimatedCost()).isEqualByComparingTo("0");
    }
}
