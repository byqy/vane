package xyz.byqy.vane.system.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;
import xyz.byqy.vane.common.error.ApiException;
import xyz.byqy.vane.common.error.CommonErrors;
import xyz.byqy.vane.system.infrastructure.SmsReceiptRepository;

class SmsReceiptServiceTest {

    private final SmsReceiptRepository receipts = mock(SmsReceiptRepository.class);
    private final SmsCallbackAuth auth = mock(SmsCallbackAuth.class);

    private final SmsReceiptService service = new SmsReceiptService(receipts, auth,
            JsonMapper.builder().build());

    private final UUID logId = UUID.randomUUID();

    private SmsCallbackAuth.CallbackPayload payload(String status) {
        return new SmsCallbackAuth.CallbackPayload("msg-1", null, status, null, Instant.now(), null,
                Map.of("raw", "value"));
    }

    @Test
    void appendsReceiptForKnownMessage() {
        when(auth.verify("aliyun-cn", "token")).thenReturn(true);
        when(receipts.findLogId("msg-1")).thenReturn(logId);
        when(receipts.exists(logId, "DELIVERED")).thenReturn(false);

        assertThat(service.record("aliyun-cn", "token", payload("DELIVERED"))).isTrue();
        verify(receipts).append(any(), any(), any(), any(), any(), any());
    }

    @Test
    void ignoresUnknownMessageId() {
        when(auth.verify("aliyun-cn", "token")).thenReturn(true);
        when(receipts.findLogId("msg-1")).thenReturn(null);

        assertThat(service.record("aliyun-cn", "token", payload("DELIVERED"))).isFalse();
        verify(receipts, never()).append(any(), any(), any(), any(), any(), any());
    }

    @Test
    void ignoresDuplicateStatus() {
        when(auth.verify("aliyun-cn", "token")).thenReturn(true);
        when(receipts.findLogId("msg-1")).thenReturn(logId);
        when(receipts.exists(logId, "DELIVERED")).thenReturn(true);

        assertThat(service.record("aliyun-cn", "token", payload("DELIVERED"))).isFalse();
        verify(receipts, never()).append(any(), any(), any(), any(), any(), any());
    }

    @Test
    void rejectsInvalidCallbackToken() {
        when(auth.verify("aliyun-cn", "bad")).thenReturn(false);

        assertThatThrownBy(() -> service.record("aliyun-cn", "bad", payload("DELIVERED")))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).errorCode())
                .isEqualTo(CommonErrors.AUTH_FORBIDDEN);
    }

    @Test
    void mapsProviderStatuses() {
        assertThat(SmsReceiptService.mapStatus(null, "DELIVRD")).isEqualTo("DELIVERED");
        assertThat(SmsReceiptService.mapStatus(null, "UNDELIV")).isEqualTo("FAILED");
        assertThat(SmsReceiptService.mapStatus("ok", null)).isEqualTo("DELIVERED");
        assertThat(SmsReceiptService.mapStatus(null, "SOMETHING_NEW")).isEqualTo("UNKNOWN");
        assertThat(SmsReceiptService.mapStatus("PENDING", null)).isEqualTo("PENDING");
    }

    @Test
    void listsReceiptsOfLog() {
        when(receipts.listByLog(logId)).thenReturn(java.util.List.of());

        assertThat(service.listByLog(logId)).isEmpty();
        verify(receipts).listByLog(logId);
    }
}
