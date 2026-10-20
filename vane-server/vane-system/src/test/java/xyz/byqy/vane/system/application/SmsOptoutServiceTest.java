package xyz.byqy.vane.system.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import xyz.byqy.vane.common.error.ApiException;
import xyz.byqy.vane.common.error.CommonErrors;
import xyz.byqy.vane.system.infrastructure.SmsOptoutRepository;

class SmsOptoutServiceTest {

    private final SmsOptoutRepository optouts = mock(SmsOptoutRepository.class);
    private final SmsPhoneValidator phones = mock(SmsPhoneValidator.class);

    private final SmsOptoutService service = new SmsOptoutService(optouts, phones);

    @Test
    void createsManualOptoutWithHash() {
        when(phones.normalize("+8613800138000"))
                .thenReturn(new SmsPhoneValidator.NormalizedPhone("+8613800138000", "CN"));
        when(optouts.insertIfAbsent(any(), eq("MARKETING"), eq("MANUAL"), any())).thenReturn(true);

        assertThat(service.create("+8613800138000", null, "客服登记")).isTrue();
        verify(optouts).insertIfAbsent(eq(SmsInboundService.sha256("+8613800138000")), eq("MARKETING"),
                eq("MANUAL"), eq("客服登记"));
    }

    @Test
    void rejectsUnknownScope() {
        assertThatThrownBy(() -> service.create("+8613800138000", "SPAM", null))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).errorCode())
                .isEqualTo(CommonErrors.VALIDATION_FAILED);
    }

    @Test
    void deleteMissingOptoutIsNotFound() {
        UUID id = UUID.randomUUID();
        when(optouts.delete(id)).thenReturn(false);

        assertThatThrownBy(() -> service.delete(id))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).errorCode())
                .isEqualTo(CommonErrors.REQUEST_NOT_FOUND);
    }
}
