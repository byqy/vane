package xyz.byqy.vane.system.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

class SmsSendWindowTest {

    private SmsSendWindow window(String utcInstant, boolean enabled, int start, int end) {
        return new SmsSendWindow(Clock.fixed(Instant.parse(utcInstant), ZoneOffset.UTC), enabled, start, end);
    }

    @Test
    void allowsCountriesInsideLocalWindow() {
        // 04:00 UTC = 12:00 北京时间，处于 8-21 点
        var allowed = window("2026-10-20T04:00:00Z", true, 8, 21).allowedCountries();

        assertThat(allowed).contains("CN", "HK", "SG", "PH");
    }

    @Test
    void excludesCountriesOutsideLocalWindow() {
        // 16:00 UTC = 次日 00:00 北京时间，不在 8-21 点
        var allowed = window("2026-10-20T16:00:00Z", true, 8, 21).allowedCountries();

        assertThat(allowed).doesNotContain("CN", "HK", "SG", "PH");
    }

    @Test
    void returnsNullWhenWindowDisabled() {
        assertThat(window("2026-10-20T16:00:00Z", false, 8, 21).allowedCountries()).isNull();
    }

    @Test
    void boundaryHourIsExclusiveAtEnd() {
        // 13:00 UTC = 21:00 北京时间，结束小时不含
        var allowed = window("2026-10-20T13:00:00Z", true, 8, 21).allowedCountries();
        assertThat(allowed).doesNotContain("CN");
    }
}
