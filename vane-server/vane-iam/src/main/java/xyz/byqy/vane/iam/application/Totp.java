package xyz.byqy.vane.iam.application;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** TOTP（RFC 6238）：HMAC-SHA1、30 秒步长、6 位数字。 */
public final class Totp {

    public static final int STEP_SECONDS = 30;
    public static final int DIGITS = 6;

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final char[] BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray();

    private Totp() {
    }

    public static byte[] newSecret() {
        byte[] secret = new byte[20];
        RANDOM.nextBytes(secret);
        return secret;
    }

    public static long stepAt(Instant instant) {
        return instant.getEpochSecond() / STEP_SECONDS;
    }

    public static String code(byte[] secret, long step) {
        try {
            byte[] counter = ByteBuffer.allocate(8).putLong(step).array();
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(secret, "HmacSHA1"));
            byte[] hash = mac.doFinal(counter);
            int offset = hash[hash.length - 1] & 0x0F;
            int binary = ((hash[offset] & 0x7F) << 24)
                    | ((hash[offset + 1] & 0xFF) << 16)
                    | ((hash[offset + 2] & 0xFF) << 8)
                    | (hash[offset + 3] & 0xFF);
            int otp = binary % (int) Math.pow(10, DIGITS);
            return String.format("%0" + DIGITS + "d", otp);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to compute TOTP", ex);
        }
    }

    /** 校验当前时间步 ±1 窗口，返回命中的时间步（用于防重放）；不通过返回 -1。 */
    public static long verify(byte[] secret, String input, Instant now) {
        if (input == null) {
            return -1;
        }
        String normalized = input.replace(" ", "");
        long current = stepAt(now);
        for (long step = current - 1; step <= current + 1; step++) {
            if (code(secret, step).equals(normalized)) {
                return step;
            }
        }
        return -1;
    }

    public static String base32(byte[] data) {
        StringBuilder sb = new StringBuilder();
        int buffer = 0;
        int bitsLeft = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xFF);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                sb.append(BASE32[(buffer >> (bitsLeft - 5)) & 0x1F]);
                bitsLeft -= 5;
            }
        }
        if (bitsLeft > 0) {
            sb.append(BASE32[(buffer << (5 - bitsLeft)) & 0x1F]);
        }
        return sb.toString();
    }

    public static String otpauthUri(String issuer, String account, byte[] secret) {
        String label = issuer + ":" + account;
        String encoded = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(label.getBytes(StandardCharsets.UTF_8));
        return "otpauth://totp/" + encoded
                + "?secret=" + base32(secret)
                + "&issuer=" + Base64.getUrlEncoder().withoutPadding()
                        .encodeToString(issuer.getBytes(StandardCharsets.UTF_8))
                + "&digits=" + DIGITS + "&period=" + STEP_SECONDS;
    }
}
