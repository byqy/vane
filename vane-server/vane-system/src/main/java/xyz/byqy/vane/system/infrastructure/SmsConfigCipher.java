package xyz.byqy.vane.system.infrastructure;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import tools.jackson.databind.ObjectMapper;

/**
 * 短信服务商配置的加解密（ADR-022），委托给通用 {@link SecretCipher}（ADR-024 泛化）。
 *
 * <p>AAD 绑定 providerCode / 数据字段用途，防止密文串用。</p>
 */
@Component
public class SmsConfigCipher {

    private final SecretCipher cipher;

    @Autowired
    public SmsConfigCipher(SecretCipher cipher) {
        this.cipher = cipher;
    }

    /** 测试与内部构造用。 */
    SmsConfigCipher(ObjectMapper objectMapper, String keyringJson, String activeVersion) {
        this(new SecretCipher(objectMapper, keyringJson, activeVersion));
    }

    /** 是否具备可用的写入密钥。 */
    public boolean configured() {
        return cipher.configured();
    }

    /** 当前写入使用的密钥版本。 */
    public String activeVersion() {
        return cipher.activeVersion();
    }

    /** 用 active 版本加密服务商配置。 */
    public String encrypt(String providerCode, String plaintext) {
        return cipher.encrypt(providerCode, plaintext);
    }

    /** 按配置记录的 key_version 解密；密钥不存在或 AAD 不匹配时抛 IllegalStateException。 */
    public String decrypt(String providerCode, String keyVersion, String cipherText) {
        return cipher.decrypt(keyVersion, providerCode, cipherText);
    }

    /** 数据字段加密（如发送日志中的号码），AAD 由调用方指定。 */
    public String encryptData(String aad, String plaintext) {
        return cipher.encrypt(aad, plaintext);
    }

    /** 按记录的历史版本解密数据字段。 */
    public String decryptData(String keyVersion, String aad, String cipherText) {
        return cipher.decrypt(keyVersion, aad, cipherText);
    }

    public boolean hasKey(String keyVersion) {
        return cipher.hasKey(keyVersion);
    }
}
