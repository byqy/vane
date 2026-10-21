package xyz.byqy.vane.system.infrastructure;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import tools.jackson.databind.ObjectMapper;

/**
 * 第三方凭据与敏感字段的加解密（ADR-022 / ADR-024）：AES-GCM + 密钥环。
 *
 * <p>主密钥来自 {@code VANE_SECRET_KEYS}（JSON：版本 → base64 的 32 字节密钥），
 * {@code VANE_SECRET_KEY_ACTIVE} 指定写入版本；未配置时回退到旧的
 * {@code VANE_SMS_SECRET_KEYS} / {@code VANE_SMS_SECRET_KEY_ACTIVE}。
 * AAD 由调用方指定，绑定用途与归属，防止密文串用。</p>
 */
@Component
public class SecretCipher {

    private static final Logger log = LoggerFactory.getLogger(SecretCipher.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int NONCE_LENGTH = 12;
    private static final int TAG_BITS = 128;

    private final Map<String, SecretKeySpec> keys;
    private final String activeVersion;

    @Autowired
    public SecretCipher(ObjectMapper objectMapper,
                        @Value("${vane.secret.keys:}") String keyringJson,
                        @Value("${vane.secret.active-key-version:}") String activeVersion,
                        @Value("${vane.sms.secret-keys:}") String legacyKeyringJson,
                        @Value("${vane.sms.active-key-version:}") String legacyActiveVersion) {
        String effectiveKeys = keyringJson == null || keyringJson.isBlank() ? legacyKeyringJson : keyringJson;
        String effectiveActive = activeVersion == null || activeVersion.isBlank()
                ? legacyActiveVersion : activeVersion;
        this.keys = parseKeyring(objectMapper, effectiveKeys);
        this.activeVersion = effectiveActive == null ? "" : effectiveActive.strip();
        if (!configured()) {
            log.warn("VANE_SECRET_KEYS / VANE_SECRET_KEY_ACTIVE 未配置，第三方凭据将无法保存");
        }
    }

    /** 测试与内部构造用。 */
    SecretCipher(ObjectMapper objectMapper, String keyringJson, String activeVersion) {
        this(objectMapper, keyringJson, activeVersion, null, null);
    }

    public boolean configured() {
        return !activeVersion.isBlank() && keys.containsKey(activeVersion);
    }

    public String activeVersion() {
        return activeVersion;
    }

    public boolean hasKey(String keyVersion) {
        return keyVersion != null && keys.containsKey(keyVersion);
    }

    /** 用 active 版本加密。 */
    public String encrypt(String aad, String plaintext) {
        return encryptWithAad(aad, plaintext, activeVersion);
    }

    /** 按记录的密钥版本解密；版本不存在或 AAD 不匹配时抛 IllegalStateException。 */
    public String decrypt(String keyVersion, String aad, String cipherText) {
        return decryptWithAad(keyVersion, aad, cipherText);
    }

    /** 稳定哈希（HMAC-SHA256），用于收件人等敏感值的精确检索。 */
    public String hash(String value) {
        if (!configured()) {
            throw new IllegalStateException("secret key is not configured");
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(keys.get(activeVersion));
            byte[] digest = mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("failed to hash value", ex);
        }
    }

    private String encryptWithAad(String aad, String plaintext, String keyVersion) {
        if (keyVersion == null || !keys.containsKey(keyVersion)) {
            throw new IllegalStateException("secret key is not configured");
        }
        try {
            byte[] nonce = new byte[NONCE_LENGTH];
            RANDOM.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, keys.get(keyVersion), new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(aad.getBytes(StandardCharsets.UTF_8));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[nonce.length + encrypted.length];
            System.arraycopy(nonce, 0, combined, 0, nonce.length);
            System.arraycopy(encrypted, 0, combined, nonce.length, encrypted.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("failed to encrypt data", ex);
        }
    }

    private String decryptWithAad(String keyVersion, String aad, String cipherText) {
        SecretKeySpec key = keys.get(keyVersion);
        if (key == null) {
            throw new IllegalStateException("secret key version " + keyVersion + " is not configured");
        }
        try {
            byte[] combined = Base64.getDecoder().decode(cipherText);
            if (combined.length <= NONCE_LENGTH) {
                throw new IllegalStateException("invalid cipher text");
            }
            byte[] nonce = new byte[NONCE_LENGTH];
            byte[] encrypted = new byte[combined.length - NONCE_LENGTH];
            System.arraycopy(combined, 0, nonce, 0, NONCE_LENGTH);
            System.arraycopy(combined, NONCE_LENGTH, encrypted, 0, encrypted.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(aad.getBytes(StandardCharsets.UTF_8));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("failed to decrypt data", ex);
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
            throw new IllegalStateException("secret keys is not a valid JSON map of version -> base64 key", ex);
        }
        raw.forEach((version, base64Key) -> {
            byte[] bytes;
            try {
                bytes = Base64.getDecoder().decode(base64Key);
            } catch (IllegalArgumentException ex) {
                throw new IllegalStateException("secret key " + version + " is not valid base64", ex);
            }
            if (bytes.length != 32) {
                throw new IllegalStateException("secret key " + version + " must be 32 bytes");
            }
            parsed.put(version, new SecretKeySpec(bytes, "AES"));
        });
        return parsed;
    }
}
