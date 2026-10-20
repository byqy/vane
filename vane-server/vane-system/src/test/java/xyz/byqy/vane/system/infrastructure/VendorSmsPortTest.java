package xyz.byqy.vane.system.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;
import xyz.byqy.vane.system.application.SmsMessage;
import xyz.byqy.vane.system.application.SmsSendResult;
import xyz.byqy.vane.system.application.SmsVendorSender;
import xyz.byqy.vane.system.infrastructure.SmsProviderRepository.SmsProviderRow;

class VendorSmsPortTest {

    private final SmsProviderRepository providers = mock(SmsProviderRepository.class);
    private final SmsConfigCipher cipher = mock(SmsConfigCipher.class);

    private static SmsMessage message() {
        return new SmsMessage("aliyun-cn", "+8613800138000", "CN", "zh-CN", "vane", "SMS_1",
                Map.of("code", "123456"));
    }

    private static SmsProviderRow row(String type) {
        return new SmsProviderRow("aliyun-cn", type, "测试", "cipher", "1", "[\"CN\"]", "[]", true, true);
    }

    private static final class FakeSender implements SmsVendorSender {
        private final String type;
        private Map<String, String> seenConfig;

        private FakeSender(String type) {
            this.type = type;
        }

        @Override
        public String providerType() {
            return type;
        }

        @Override
        public SmsSendResult send(Map<String, String> config, SmsMessage message) {
            this.seenConfig = config;
            return SmsSendResult.ok("msg-1", "OK");
        }
    }

    @Test
    void routesByProviderTypeAfterDecryptingConfig() {
        FakeSender aliyun = new FakeSender("ALIYUN");
        FakeSender tencent = new FakeSender("TENCENT");
        VendorSmsPort port = new VendorSmsPort(providers, cipher, JsonMapper.builder().build(),
                List.of(aliyun, tencent));
        when(providers.findByCode("aliyun-cn")).thenReturn(Optional.of(row("ALIYUN")));
        when(cipher.decrypt("aliyun-cn", "1", "cipher")).thenReturn("{\"accessKeyId\":\"ak\",\"accessKeySecret\":\"sk\"}");

        SmsSendResult result = port.send(message());

        assertThat(result.success()).isTrue();
        assertThat(result.providerMessageId()).isEqualTo("msg-1");
        assertThat(aliyun.seenConfig).containsEntry("accessKeyId", "ak");
        assertThat(tencent.seenConfig).isNull();
    }

    @Test
    void rejectsUnknownProvider() {
        VendorSmsPort port = new VendorSmsPort(providers, cipher, JsonMapper.builder().build(), List.of());
        when(providers.findByCode("aliyun-cn")).thenReturn(Optional.empty());

        assertThat(port.send(message()).errorCode()).isEqualTo("SMS.PROVIDER.NOT_FOUND");
    }

    @Test
    void rejectsUnsupportedProviderType() {
        VendorSmsPort port = new VendorSmsPort(providers, cipher, JsonMapper.builder().build(), List.of());
        when(providers.findByCode("aliyun-cn")).thenReturn(Optional.of(row("GENERIC_HTTP")));

        assertThat(port.send(message()).errorCode()).isEqualTo("SMS.VENDOR.UNSUPPORTED");
    }

    @Test
    void reportsConfigDecryptFailure() {
        VendorSmsPort port = new VendorSmsPort(providers, cipher, JsonMapper.builder().build(),
                List.of(new FakeSender("ALIYUN")));
        when(providers.findByCode("aliyun-cn")).thenReturn(Optional.of(row("ALIYUN")));
        when(cipher.decrypt("aliyun-cn", "1", "cipher")).thenThrow(new IllegalStateException("bad key"));

        assertThat(port.send(message()).errorCode()).isEqualTo("SMS.CONFIG.DECRYPT_FAILED");
    }
}
