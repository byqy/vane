package xyz.byqy.vane.system.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.SecureRandom;
import java.util.Base64;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

class SmsConfigCipherTest {

    private static String key() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }

    private static SmsConfigCipher cipher(String keyring, String active) {
        return new SmsConfigCipher(JsonMapper.builder().build(), keyring, active);
    }

    @Test
    void encryptsAndDecryptsWithActiveKey() {
        String keyring = "{\"1\":\"" + key() + "\"}";
        SmsConfigCipher cipher = cipher(keyring, "1");

        String encrypted = cipher.encrypt("aliyun-cn", "{\"accessKeyId\":\"ak\",\"accessKeySecret\":\"sk\"}");

        assertThat(encrypted).isNotBlank();
        assertThat(cipher.decrypt("aliyun-cn", "1", encrypted))
                .isEqualTo("{\"accessKeyId\":\"ak\",\"accessKeySecret\":\"sk\"}");
        assertThat(cipher.encrypt("aliyun-cn", "same")).isNotEqualTo(encrypted);
    }

    @Test
    void rejectsCipherTextFromAnotherProvider() {
        String keyring = "{\"1\":\"" + key() + "\"}";
        SmsConfigCipher cipher = cipher(keyring, "1");
        String encrypted = cipher.encrypt("aliyun-cn", "secret");

        assertThatThrownBy(() -> cipher.decrypt("tencent-cn", "1", encrypted))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsUnknownKeyVersion() {
        SmsConfigCipher cipher = cipher("{\"1\":\"" + key() + "\"}", "1");
        String encrypted = cipher.encrypt("aliyun-cn", "secret");

        assertThatThrownBy(() -> cipher.decrypt("aliyun-cn", "2", encrypted))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("2");
    }

    @Test
    void refusesToEncryptWithoutActiveKey() {
        SmsConfigCipher cipher = cipher("{}", "");

        assertThat(cipher.configured()).isFalse();
        assertThatThrownBy(() -> cipher.encrypt("aliyun-cn", "secret")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsKeyThatIsNot32Bytes() {
        String shortKey = Base64.getEncoder().encodeToString(new byte[16]);
        assertThatThrownBy(() -> cipher("{\"1\":\"" + shortKey + "\"}", "1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
    }

    @Test
    void supportsMultipleKeyVersionsForRotation() {
        String key1 = key();
        String key2 = key();
        String keyring = "{\"1\":\"" + key1 + "\",\"2\":\"" + key2 + "\"}";
        SmsConfigCipher cipher = cipher(keyring, "1");
        String withKey1 = cipher.encrypt("aliyun-cn", "old-config");

        SmsConfigCipher rotated = cipher(keyring, "2");
        assertThat(rotated.decrypt("aliyun-cn", "1", withKey1)).isEqualTo("old-config");
        assertThat(rotated.decrypt("aliyun-cn", "2", rotated.encrypt("aliyun-cn", "new-config")))
                .isEqualTo("new-config");
    }

    @Test
    void encryptsDataWithCustomAad() {
        SmsConfigCipher cipher = cipher("{\"1\":\"" + key() + "\"}", "1");

        String encrypted = cipher.encryptData("sys_sms_log", "+8613800138000");

        assertThat(cipher.decryptData("1", "sys_sms_log", encrypted)).isEqualTo("+8613800138000");
        assertThatThrownBy(() -> cipher.decryptData("1", "other-aad", encrypted))
                .isInstanceOf(IllegalStateException.class);
    }
}
