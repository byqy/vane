package xyz.byqy.vane.system.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import xyz.byqy.vane.system.infrastructure.SmsCampaignRepository;
import xyz.byqy.vane.system.infrastructure.SmsCampaignRepository.ProviderCount;

/** 短信发送统计与成本估算（ADR-022 P3）。 */
@Service
public class SmsStatsService {

    private final SmsCampaignRepository campaigns;
    private final BigDecimal costPerMessage;

    public SmsStatsService(SmsCampaignRepository campaigns,
                           @Value("${vane.sms.cost-per-message:0}") BigDecimal costPerMessage) {
        this.campaigns = campaigns;
        this.costPerMessage = costPerMessage;
    }

    @Transactional(readOnly = true)
    public Overview overview(Instant from, Instant to, Instant now) {
        Instant effectiveTo = to == null ? now : to;
        Instant effectiveFrom = from == null ? effectiveTo.minusSeconds(30L * 24 * 3600) : from;
        Map<String, long[]> byProvider = new LinkedHashMap<>();
        long sent = 0;
        long failed = 0;
        for (ProviderCount count : campaigns.stats(effectiveFrom, effectiveTo)) {
            long[] values = byProvider.computeIfAbsent(count.providerCode(), key -> new long[2]);
            if ("SUCCESS".equals(count.result())) {
                values[0] += count.count();
                sent += count.count();
            } else {
                values[1] += count.count();
                failed += count.count();
            }
        }
        List<ProviderStat> providers = new ArrayList<>();
        byProvider.forEach((provider, values) -> providers.add(
                new ProviderStat(provider, values[0], values[1], costPerMessage.multiply(BigDecimal.valueOf(values[0])))));
        return new Overview(effectiveFrom, effectiveTo, sent + failed, sent, failed,
                costPerMessage.multiply(BigDecimal.valueOf(sent)), providers);
    }

    public record ProviderStat(String providerCode, long sent, long failed, BigDecimal estimatedCost) {
    }

    public record Overview(Instant from, Instant to, long total, long sent, long failed,
                           BigDecimal estimatedCost, List<ProviderStat> byProvider) {
    }
}
