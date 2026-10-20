package xyz.byqy.vane.system.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import tools.jackson.databind.json.JsonMapper;
import xyz.byqy.vane.system.infrastructure.SmsConfigCipher;
import xyz.byqy.vane.system.infrastructure.SmsLogRepository;
import xyz.byqy.vane.system.infrastructure.SmsLogRepository.ReceiptCandidate;
import xyz.byqy.vane.system.infrastructure.SmsProviderRepository;
import xyz.byqy.vane.system.infrastructure.SmsProviderRepository.SmsProviderRow;

class SmsReceiptPollHandlerTest {

    private final SmsLogRepository logs = mock(SmsLogRepository.class);
    private final SmsProviderRepository providers = mock(SmsProviderRepository.class);
    private final SmsConfigCipher cipher = mock(SmsConfigCipher.class);
    private final SmsReceiptService receipts = mock(SmsReceiptService.class);
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);

    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> valueOps = mock(ValueOperations.class);

    private final UUID logId = UUID.randomUUID();

    private final SmsReceiptPoller fakePoller = new SmsReceiptPoller() {
        @Override
        public String providerType() {
            return "ALIYUN";
        }

        @Override
        public List<PolledReceipt> poll(Map<String, String> config, List<PollTarget> targets) {
            return targets.stream().map(target -> new PolledReceipt(target.logId(), "DELIVERED", "DELIVRD",
                    Instant.parse("2026-10-20T10:00:00Z"), "{}")).toList();
        }
    };

    private final SmsReceiptPollHandler handler = new SmsReceiptPollHandler(logs, providers, cipher,
            JsonMapper.builder().build(), receipts, redis, List.of(fakePoller));

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
    }

    private ReceiptCandidate candidate() {
        return new ReceiptCandidate(logId, "aliyun-cn", "msg-1", "cipher-phone", "1", Instant.now());
    }

    private void givenProviderAndCipher() {
        when(logs.receiptCandidates(200)).thenReturn(List.of(candidate()));
        when(providers.findByCode("aliyun-cn")).thenReturn(Optional.of(new SmsProviderRow("aliyun-cn", "ALIYUN",
                "阿里云", "cipher", "1", "[\"CN\"]", "[]", true, true)));
        when(cipher.decrypt("aliyun-cn", "1", "cipher")).thenReturn("{\"accessKeyId\":\"ak\"}");
        when(cipher.decryptData("1", "sys_sms_log", "cipher-phone")).thenReturn("+8613800138000");
    }

    @Test
    void appendsPolledReceipts() {
        givenProviderAndCipher();
        when(receipts.recordFromProvider(eq("aliyun-cn"), eq(logId), eq("DELIVERED"), any(), any(), any()))
                .thenReturn(true);

        handler.run(null);

        verify(receipts).recordFromProvider(eq("aliyun-cn"), eq(logId), eq("DELIVERED"), eq("DELIVRD"),
                any(), any());
        verify(redis).delete("vane:sms:receipt:poll:lock");
    }

    @Test
    void skipsWhenAnotherInstanceHoldsLock() {
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(false);

        handler.run(null);

        verify(logs, never()).receiptCandidates(200);
    }

    @Test
    void skipsCandidatesWhoseProviderIsDisabled() {
        when(logs.receiptCandidates(200)).thenReturn(List.of(candidate()));
        when(providers.findByCode("aliyun-cn")).thenReturn(Optional.of(new SmsProviderRow("aliyun-cn", "ALIYUN",
                "阿里云", "cipher", "1", "[\"CN\"]", "[]", false, true)));

        handler.run(null);

        verify(receipts, never()).recordFromProvider(any(), any(), any(), any(), any(), any());
    }

    @Test
    void skipsCandidatesWhenPhoneCannotBeDecrypted() {
        givenProviderAndCipher();
        when(cipher.decryptData("1", "sys_sms_log", "cipher-phone"))
                .thenThrow(new IllegalStateException("missing key"));

        handler.run(null);

        verify(receipts, never()).recordFromProvider(any(), any(), any(), any(), any(), any());
    }

    @Test
    void skipsProvidersWithoutPoller() {
        when(logs.receiptCandidates(200)).thenReturn(List.of(candidate()));
        when(providers.findByCode("aliyun-cn")).thenReturn(Optional.of(new SmsProviderRow("aliyun-cn", "TENCENT",
                "腾讯云", "cipher", "1", "[\"CN\"]", "[]", true, true)));

        handler.run(null);

        verify(receipts, never()).recordFromProvider(any(), any(), any(), any(), any(), any());
    }
}
