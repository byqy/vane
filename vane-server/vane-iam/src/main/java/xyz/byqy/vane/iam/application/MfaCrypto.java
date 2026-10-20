package xyz.byqy.vane.iam.application;

import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * TOTP 密钥的对称加密（AES-256-GCM）。
 * 主密钥来自 vane.auth.mfa.secret-key（base64 的 32 字节），不得入库。
 * 未配置时使用开发密钥并打 WARN —— 生产必须配置。
 */
@Component
public class MfaCrypto {

    private static final Logger log = LoggerFactory.getLogger(MfaCrypto.class);
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;

    private final SecretKey key;
    private final int keyVersion;
    private final SecureRandom random = new SecureRandom();

    public MfaCrypto(@Value("${vane.auth.mfa.secret-key:}") String base64Key,
                     @Value("${vane.auth.mfa.key-version:1}") int keyVersion) {
        byte[] raw;
        if (base64Key == null || base64Key.isBlank()) {
            log.warn("vane.auth.mfa.secret-key is not configured, using insecure development key");
            raw = new byte[32];
        } else {
            raw = Base64.getDecoder().decode(base64Key);
            if (raw.length != 32) {
                throw new IllegalStateException("vane.auth.mfa.secret-key must be 32 bytes (base64)");
            }
        }
        this.key = new SecretKeySpec(raw, "AES");
        this.keyVersion = keyVersion;
    }

    public String encrypt(byte[] plain) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plain);
            ByteBuffer buffer = ByteBuffer.allocate(iv.length + encrypted.length);
            buffer.put(iv).put(encrypted);
            return Base64.getEncoder().encodeToString(buffer.array());
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to encrypt MFA secret", ex);
        }
    }

    public byte[] decrypt(String cipherText) {
        try {
            byte[] all = Base64.getDecoder().decode(cipherText);
            ByteBuffer buffer = ByteBuffer.wrap(all);
            byte[] iv = new byte[IV_LENGTH];
            buffer.get(iv);
            byte[] encrypted = new byte[buffer.remaining()];
            buffer.get(encrypted);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            return cipher.doFinal(encrypted);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to decrypt MFA secret", ex);
        }
    }

    public int keyVersion() {
        return keyVersion;
    }
}
