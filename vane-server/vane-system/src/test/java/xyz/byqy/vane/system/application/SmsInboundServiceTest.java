package xyz.byqy.vane.system.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;
import xyz.byqy.vane.common.error.ApiException;
import xyz.byqy.vane.common.error.CommonErrors;
import xyz.byqy.vane.system.infrastructure.SmsConfigCipher;
import xyz.byqy.vane.system.infrastructure.SmsInboundRepository;
import xyz.byqy.vane.system.infrastructure.SmsOptoutRepository;
import xyz.byqy.vane.system.infrastructure.SmsProviderRepository;
import xyz.byqy.vane.system.infrastructure.SmsProviderRepository.SmsProviderRow;

class SmsInboundServiceTest {

    private final SmsInboundRepository inbounds = mock(SmsInboundRepository.class);
    private final SmsOptoutRepository optouts = mock(SmsOptoutRepository.class);
    private final SmsProviderRepository providers = mock(SmsProviderRepository.class);
    private final SmsConfigCipher cipher = mock(SmsConfigCipher.class);
    private final SmsCallbackAuth auth = mock(SmsCallbackAuth.class);
    private final SmsPhoneValidator phones = mock(SmsPhoneValidator.class);

    private final SmsInboundService service = new SmsInboundService(inbounds, optouts, providers, cipher, auth,
            phones, JsonMapper.builder().build());

    private SmsCallbackAuth.CallbackPayload payload(String content) {
        return new SmsCallbackAuth.CallbackPayload("in-1", "+8613800138000", null, null, Instant.now(), content,
                Map.of());
    }

    private void givenProviderWithKeyword(String keywordsJson) {
        when(providers.findByCode("aliyun-cn")).thenReturn(Optional.of(new SmsProviderRow("aliyun-cn", "ALIYUN",
                "阿里云", "cipher", "1", "[\"CN\"]", "[]", true, true)));
        when(cipher.decrypt("aliyun-cn", "1", "cipher"))
                .thenReturn("{\"optoutKeywords\":\"" + keywordsJson + "\"}");
        when(phones.normalize("+8613800138000"))
                .thenReturn(new SmsPhoneValidator.NormalizedPhone("+8613800138000", "CN"));
    }

    @Test
    void keywordReplyCreatesOptoutAndMarksProcessed() {
        when(auth.verify("aliyun-cn", "token")).thenReturn(true);
        givenProviderWithKeyword("[\\\"TD\\\"]");

        service.record("aliyun-cn", "token", payload("TD"));

        verify(optouts).insertIfAbsent(eq(SmsInboundService.sha256("+8613800138000")), eq("MARKETING"),
                eq("INBOUND"), eq("TD"));
        verify(inbounds).append(eq("aliyun-cn"), eq("in-1"), anyString(), anyString(), eq("TD"), any(),
                eq(true), eq("TD"));
    }

    @Test
    void plainReplyIsStoredWithoutOptout() {
        when(auth.verify("aliyun-cn", "token")).thenReturn(true);
        givenProviderWithKeyword("[]");

        service.record("aliyun-cn", "token", payload("你好"));

        verify(optouts, never()).insertIfAbsent(any(), any(), any(), any());
        verify(inbounds).append(eq("aliyun-cn"), eq("in-1"), anyString(), anyString(), eq("你好"), any(),
                eq(false), eq(null));
    }

    @Test
    void rejectsInvalidToken() {
        when(auth.verify("aliyun-cn", "bad")).thenReturn(false);

        assertThatThrownBy(() -> service.record("aliyun-cn", "bad", payload("TD")))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).errorCode())
                .isEqualTo(CommonErrors.AUTH_FORBIDDEN);
    }

    @Test
    void rejectsEmptyContent() {
        when(auth.verify("aliyun-cn", "token")).thenReturn(true);

        assertThatThrownBy(() -> service.record("aliyun-cn", "token",
                new SmsCallbackAuth.CallbackPayload("in-1", "+8613800138000", null, null, Instant.now(), " ", Map.of())))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).errorCode())
                .isEqualTo(CommonErrors.VALIDATION_FAILED);
    }

}
