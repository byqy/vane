package xyz.byqy.vane.system.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.ObjectMapper;
import xyz.byqy.vane.system.infrastructure.SmsConfigCipher;
import xyz.byqy.vane.system.infrastructure.SmsProviderRepository;

/** 回调鉴权：校验 provider 配置里的 callbackToken（常量时间比较）。 */
@Service
public class SmsCallbackAuth {

    private final SmsProviderRepository providers;
    private final SmsConfigCipher cipher;
    private final ObjectMapper json;

    public SmsCallbackAuth(SmsProviderRepository providers, SmsConfigCipher cipher, ObjectMapper json) {
        this.providers = providers;
        this.cipher = cipher;
        this.json = json;
    }

    @Transactional(readOnly = true)
    public boolean verify(String providerCode, String token) {
        return providers.findByCode(providerCode)
                .map(row -> {
                    String expected;
                    try {
                        @SuppressWarnings("unchecked")
                        Map<String, String> config = json.readValue(
                                cipher.decrypt(row.code(), row.keyVersion(), row.configCipher()), Map.class);
                        expected = config.getOrDefault("callbackToken", "");
                    } catch (RuntimeException ex) {
                        return false;
                    }
                    return !expected.isBlank() && MessageDigest.isEqual(
                            expected.getBytes(StandardCharsets.UTF_8),
                            (token == null ? "" : token).getBytes(StandardCharsets.UTF_8));
                })
                .orElse(false);
    }

    /** 通用回调体：厂商差异由适配层解析，这里只承载标准字段。 */
    public record CallbackPayload(String providerMessageId, String phone, String status, String providerStatus,
                                  Instant occurredAt, String content, Map<String, Object> raw) {
    }

    /** 上/下行关键字默认值。 */
    public static List<String> defaultOptoutKeywords() {
        return List.of("TD", "退订", "STOP", "UNSUB", "取消");
    }
}
