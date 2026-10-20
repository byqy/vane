package xyz.byqy.vane.system.application;

import java.time.Clock;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 群发发送时段（ADR-022 P3）：按号码国家映射时区，默认 8:00–21:00 之外不发送。
 * 国家没有时区映射时按 UTC 处理。
 */
@Component
public class SmsSendWindow {

    private static final Map<String, String> ZONES = Map.ofEntries(
            Map.entry("CN", "Asia/Shanghai"),
            Map.entry("HK", "Asia/Hong_Kong"),
            Map.entry("MO", "Asia/Macau"),
            Map.entry("TW", "Asia/Taipei"),
            Map.entry("SG", "Asia/Singapore"),
            Map.entry("MY", "Asia/Kuala_Lumpur"),
            Map.entry("TH", "Asia/Bangkok"),
            Map.entry("VN", "Asia/Ho_Chi_Minh"),
            Map.entry("ID", "Asia/Jakarta"),
            Map.entry("PH", "Asia/Manila"));

    private final Clock clock;
    private final int startHour;
    private final int endHour;
    private final boolean enabled;

    @Autowired
    public SmsSendWindow(@Value("${vane.sms.campaign.window-enabled:true}") boolean enabled,
                         @Value("${vane.sms.campaign.window-start:8}") int startHour,
                         @Value("${vane.sms.campaign.window-end:21}") int endHour) {
        this(Clock.systemUTC(), enabled, startHour, endHour);
    }

    SmsSendWindow(Clock clock, boolean enabled, int startHour, int endHour) {
        this.clock = clock;
        this.enabled = enabled;
        this.startHour = Math.max(0, Math.min(startHour, 23));
        this.endHour = Math.max(1, Math.min(endHour, 24));
    }

    /** 当前处于允许发送时段的国家集合；未启用时段限制时返回 null（表示不过滤）。 */
    public Set<String> allowedCountries() {
        if (!enabled) {
            return null;
        }
        Set<String> allowed = new LinkedHashSet<>();
        for (Map.Entry<String, String> entry : ZONES.entrySet()) {
            if (inWindow(entry.getValue())) {
                allowed.add(entry.getKey());
            }
        }
        return allowed;
    }

    private boolean inWindow(String zone) {
        int hour = LocalTime.now(clock.withZone(ZoneId.of(zone))).getHour();
        return hour >= startHour && hour < endHour;
    }
}
