package xyz.byqy.vane.system.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import xyz.byqy.vane.common.error.ApiException;
import xyz.byqy.vane.common.error.CommonErrors;

/**
 * 短信验证码（ADR-022）：发送走 {@link SmsSendService}，验证码只以随机盐 + SHA-256 存 Valkey，
 * 5 分钟有效、最多尝试 5 次、验证成功即失效。
 */
@Service
public class SmsVerificationService {

    private static final Pattern CODE_PATTERN = Pattern.compile("^\\d{6}$");
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Duration TTL = Duration.ofMinutes(5);
    private static final int MAX_ATTEMPTS = 5;
    private static final String DEFAULT_LOCALE = "zh-CN";
    private static final Set<String> SCENES = Set.of(
            "LOGIN", "RESET_PASSWORD", "BIND_PHONE", "SENSITIVE_OPERATION");

    private final StringRedisTemplate redis;
    private final SmsPhoneValidator phones;
    private final SmsSendService sms;
    private final String templateCode;

    public SmsVerificationService(StringRedisTemplate redis, SmsPhoneValidator phones, SmsSendService sms,
                                  @Value("${vane.sms.verify-template-code:password.reset}") String templateCode) {
        this.redis = redis;
        this.phones = phones;
        this.sms = sms;
        this.templateCode = templateCode;
    }

    /** 生成并发送验证码；发送成功后才写入存储，避免产生「发了没到却能用」的码。 */
    public void request(String scene, String phone, String locale) {
        validateScene(scene);
        SmsPhoneValidator.NormalizedPhone normalized = phones.normalize(phone);
        String code = "%06d".formatted(RANDOM.nextInt(1_000_000));
        sms.send(templateCode, normalized.e164(), locale == null || locale.isBlank() ? DEFAULT_LOCALE : locale,
                Map.of("code", code, "minutes", String.valueOf(TTL.toMinutes())), "VERIFY_CODE");
        String salt = randomHex(16);
        redis.opsForValue().set(codeKey(scene, normalized.e164()), salt + ":" + hash(salt + code), TTL);
        redis.delete(attemptsKey(scene, normalized.e164()));
    }

    /** 校验验证码；成功即删除，失败累计尝试次数。 */
    public void verify(String scene, String phone, String code) {
        validateScene(scene);
        if (code == null || !CODE_PATTERN.matcher(code).matches()) {
            throw new ApiException(CommonErrors.SMS_CODE_INVALID);
        }
        SmsPhoneValidator.NormalizedPhone normalized = phones.normalize(phone);
        String key = codeKey(scene, normalized.e164());
        String stored = redis.opsForValue().get(key);
        if (stored == null) {
            throw new ApiException(CommonErrors.SMS_CODE_EXPIRED);
        }
        int separator = stored.indexOf(':');
        if (separator <= 0) {
            redis.delete(key);
            throw new ApiException(CommonErrors.SMS_CODE_EXPIRED);
        }
        String expected = stored.substring(separator + 1);
        String actual = hash(stored.substring(0, separator) + code);
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8))) {
            Long attempts = redis.opsForValue().increment(attemptsKey(scene, normalized.e164()));
            if (attempts != null && attempts >= MAX_ATTEMPTS) {
                redis.delete(key);
                throw new ApiException(CommonErrors.SMS_CODE_ATTEMPTS_EXCEEDED);
            }
            throw new ApiException(CommonErrors.SMS_CODE_INVALID);
        }
        redis.delete(key);
        redis.delete(attemptsKey(scene, normalized.e164()));
    }

    private void validateScene(String scene) {
        if (scene == null || !SCENES.contains(scene)) {
            throw new ApiException(CommonErrors.VALIDATION_FAILED);
        }
    }

    private static String codeKey(String scene, String phoneE164) {
        return "vane:sms:code:" + scene + ":" + hash(phoneE164);
    }

    private static String attemptsKey(String scene, String phoneE164) {
        return "vane:sms:code:attempts:" + scene + ":" + hash(phoneE164);
    }

    private static String randomHex(int bytes) {
        byte[] buffer = new byte[bytes];
        RANDOM.nextBytes(buffer);
        return HexFormat.of().formatHex(buffer);
    }

    private static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
