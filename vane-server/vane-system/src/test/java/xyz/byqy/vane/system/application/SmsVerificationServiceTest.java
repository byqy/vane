package xyz.byqy.vane.system.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import xyz.byqy.vane.common.error.ApiException;
import xyz.byqy.vane.common.error.CommonErrors;

class SmsVerificationServiceTest {

    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final SmsPhoneValidator phones = mock(SmsPhoneValidator.class);
    private final SmsSendService sms = mock(SmsSendService.class);

    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> valueOps = mock(ValueOperations.class);

    private final SmsVerificationService service = new SmsVerificationService(redis, phones, sms, "password.reset");

    private static final String PHONE = "+8613800138000";
    private static final String SCENE = "LOGIN";
    private static final String SALT = "00112233445566778899aabbccddeeff";

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(phones.normalize(PHONE))
                .thenReturn(new SmsPhoneValidator.NormalizedPhone(PHONE, "CN"));
        when(sms.send(anyString(), anyString(), anyString(), any(), anyString()))
                .thenReturn(new SmsSendService.SendOutcome("aliyun-cn", "msg-1", "+8613****8000", "OK"));
    }

    private static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static String codeKey() {
        return "vane:sms:code:" + SCENE + ":" + hash(PHONE);
    }

    private static String attemptsKey() {
        return "vane:sms:code:attempts:" + SCENE + ":" + hash(PHONE);
    }

    @Test
    void sendsCodeAndStoresOnlySaltedHash() {

        service.request(SCENE, PHONE, "zh-CN");

        ArgumentCaptor<Map<String, String>> variables = ArgumentCaptor.forClass(Map.class);
        verify(sms).send(eq("password.reset"), eq(PHONE), eq("zh-CN"), variables.capture(), eq("VERIFY_CODE"));
        String code = variables.getValue().get("code");
        assertThat(code).matches("\\d{6}");
        assertThat(variables.getValue().get("minutes")).isEqualTo("5");

        ArgumentCaptor<String> stored = ArgumentCaptor.forClass(String.class);
        verify(valueOps).set(eq(codeKey()), stored.capture(), any(Duration.class));
        assertThat(stored.getValue()).matches("[0-9a-f]{32}:[0-9a-f]{64}");
        assertThat(stored.getValue()).doesNotContain(code);
    }

    @Test
    void defaultsLocaleToChinese() {

        service.request(SCENE, PHONE, null);

        verify(sms).send(eq("password.reset"), eq(PHONE), eq("zh-CN"), any(), eq("VERIFY_CODE"));
    }

    @Test
    void rejectsUnknownScene() {
        assertThatThrownBy(() -> service.request("UNKNOWN", PHONE, "zh-CN"))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).errorCode())
                .isEqualTo(CommonErrors.VALIDATION_FAILED);
        verify(sms, never()).send(anyString(), anyString(), anyString(), any(), anyString());
    }

    @Test
    void verifiesCorrectCodeAndConsumesIt() {
        when(valueOps.get(codeKey())).thenReturn(SALT + ":" + hash(SALT + "123456"));

        service.verify(SCENE, PHONE, "123456");

        verify(redis).delete(codeKey());
        verify(redis).delete(attemptsKey());
    }

    @Test
    void rejectsWrongCodeAndCountsAttempt() {
        when(valueOps.get(codeKey())).thenReturn(SALT + ":" + hash(SALT + "123456"));
        when(valueOps.increment(attemptsKey())).thenReturn(1L);

        assertThatThrownBy(() -> service.verify(SCENE, PHONE, "000000"))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).errorCode())
                .isEqualTo(CommonErrors.SMS_CODE_INVALID);
    }

    @Test
    void blocksAfterTooManyAttempts() {
        when(valueOps.get(codeKey())).thenReturn(SALT + ":" + hash(SALT + "123456"));
        when(valueOps.increment(attemptsKey())).thenReturn(5L);

        assertThatThrownBy(() -> service.verify(SCENE, PHONE, "000000"))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).errorCode())
                .isEqualTo(CommonErrors.SMS_CODE_ATTEMPTS_EXCEEDED);
        verify(redis).delete(codeKey());
    }

    @Test
    void rejectsExpiredCode() {
        when(valueOps.get(codeKey())).thenReturn(null);

        assertThatThrownBy(() -> service.verify(SCENE, PHONE, "123456"))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).errorCode())
                .isEqualTo(CommonErrors.SMS_CODE_EXPIRED);
    }

    @Test
    void rejectsMalformedCodeWithoutTouchingRedis() {
        assertThatThrownBy(() -> service.verify(SCENE, PHONE, "12ab"))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).errorCode())
                .isEqualTo(CommonErrors.SMS_CODE_INVALID);
        verify(redis, never()).opsForValue();
    }
}
