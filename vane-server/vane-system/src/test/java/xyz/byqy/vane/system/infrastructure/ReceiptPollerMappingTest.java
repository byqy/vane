package xyz.byqy.vane.system.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ReceiptPollerMappingTest {

    @Test
    void aliyunMapsSendStatus() {
        assertThat(AliyunReceiptPoller.mapStatus(1L)).isEqualTo("PENDING");
        assertThat(AliyunReceiptPoller.mapStatus(2L)).isEqualTo("FAILED");
        assertThat(AliyunReceiptPoller.mapStatus(3L)).isEqualTo("DELIVERED");
        assertThat(AliyunReceiptPoller.mapStatus(9L)).isEqualTo("UNKNOWN");
        assertThat(AliyunReceiptPoller.mapStatus(null)).isEqualTo("UNKNOWN");
    }

    @Test
    void tencentMapsReportStatus() {
        assertThat(TencentReceiptPoller.mapStatus("DELIVRD")).isEqualTo("DELIVERED");
        assertThat(TencentReceiptPoller.mapStatus("SUCCESS")).isEqualTo("DELIVERED");
        assertThat(TencentReceiptPoller.mapStatus("UNDELIV")).isEqualTo("FAILED");
        assertThat(TencentReceiptPoller.mapStatus("REJECTD")).isEqualTo("FAILED");
        assertThat(TencentReceiptPoller.mapStatus("PENDING")).isEqualTo("UNKNOWN");
        assertThat(TencentReceiptPoller.mapStatus(null)).isEqualTo("UNKNOWN");
    }
}
