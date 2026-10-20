package xyz.byqy.vane.system.infrastructure;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import tools.jackson.databind.ObjectMapper;

/**
 * 短信服务商配置的加解密（ADR-022）：AES-GCM + 密钥环。
 *
 * <p>主密钥来自环境变量 {@code VANE_SMS_SECRET_KEYS}（JSON：版本 → base64 的 32 字节密钥），
 * {@code VANE_SMS_SECRET_KEY_ACTIVE} 指定写入使用的版本；AAD 绑定 providerCode 防止密文串用。</p>
 */
@Component
public class SmsConfigCipher {

    private static final Logger log = LoggerFactory.getLogger(SmsConfigCipher.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int NONCE_LENGTH = 12;
    private static final int TAG_BITS = 128;

    private final Map<String, SecretKeySpec> keys;
    private final String activeVersion;

    public SmsConfigCipher(ObjectMapper objectMapper,
                           @Value("${vane.sms.secret-keys:{}}") String keyringJson,
                           @Value("${vane.sms.active-key-version:}") String activeVersion) {
        this.keys = parseKeyring(objectMapper, keyringJson);
        this.activeVersion = activeVersion == null ? "" : activeVersion.strip();
        if (!configured()) {
            log.warn("vane.sms.secret-keys / vane.sms.active-key-version 未配置，短信服务商配置将无法保存");
        }
    }

    /** 是否具备可用的写入密钥。 */
    public boolean configured() {
        return !activeVersion.isBlank() && keys.containsKey(activeVersion);
    }

    /** 当前写入使用的密钥版本。 */
    public String activeVersion() {
        return activeVersion;
    }

    /** 用 active 版本加密；未配置密钥时抛 IllegalStateException，由调用方转成 SMS.NOT_CONFIGURED。 */
    public String encrypt(String providerCode, String plaintext) {
        if (!configured()) {
            throw new IllegalStateException("SMS secret key is not configured");
        }
        try {
            byte[] nonce = new byte[NONCE_LENGTH];
            RANDOM.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, keys.get(activeVersion), new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(providerCode.getBytes(StandardCharsets.UTF_8));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[nonce.length + encrypted.length];
            System.arraycopy(nonce, 0, combined, 0, nonce.length);
            System.arraycopy(encrypted, 0, combined, nonce.length, encrypted.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("failed to encrypt SMS provider config", ex);
        }
    }

    /** 按配置记录的 key_version 解密；密钥不存在或 AAD 不匹配时抛 IllegalStateException。 */
    public String decrypt(String providerCode, String keyVersion, String cipherText) {
        SecretKeySpec key = keys.get(keyVersion);
        if (key == null) {
            throw new IllegalStateException("SMS secret key version " + keyVersion + " is not configured");
        }
        try {
            byte[] combined = Base64.getDecoder().decode(cipherText);
            if (combined.length <= NONCE_LENGTH) {
                throw new IllegalStateException("invalid SMS provider cipher text");
            }
            byte[] nonce = new byte[NONCE_LENGTH];
            byte[] encrypted = new byte[combined.length - NONCE_LENGTH];
            System.arraycopy(combined, 0, nonce, 0, NONCE_LENGTH);
            System.arraycopy(combined, NONCE_LENGTH, encrypted, 0, encrypted.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(providerCode.getBytes(StandardCharsets.UTF_8));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("failed to decrypt SMS provider config", ex);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, SecretKeySpec> parseKeyring(ObjectMapper objectMapper, String json) {
        Map<String, SecretKeySpec> parsed = new LinkedHashMap<>();
        if (json == null || json.isBlank() || "{}".equals(json.strip())) {
            return parsed;
        }
        Map<String, String> raw;
        try {
            raw = objectMapper.readValue(json, Map.class);
        } catch (RuntimeException ex) {
            throw new IllegalStateException(
                    "vane.sms.secret-keys is not a valid JSON map of version -> base64 key", ex);
        }
        raw.forEach((version, base64Key) -> {
            byte[] bytes;
            try {
                bytes = Base64.getDecoder().decode(base64Key);
            } catch (IllegalArgumentException ex) {
                throw new IllegalStateException("SMS secret key " + version + " is not valid base64", ex);
            }
            if (bytes.length != 32) {
                throw new IllegalStateException("SMS secret key " + version + " must be 32 bytes");
            }
            parsed.put(version, new SecretKeySpec(bytes, "AES"));
        });
        return parsed;
    }
}
