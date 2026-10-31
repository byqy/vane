package xyz.byqy.vane.bootstrap.idempotency;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Pattern;

import jakarta.servlet.http.HttpServletRequest;

/** 幂等协议的公共约定：头名、适用范围与指纹算法。 */
public final class IdempotencySupport {

    /** 客户端提供的幂等键头。 */
    public static final String HEADER = "Idempotency-Key";

    /** 重放响应标记头。 */
    public static final String REPLAY_HEADER = "Idempotent-Replayed";

    /** 请求属性：本次请求占用的存储键。 */
    public static final String ATTR_SCOPE_KEY = "vane.idempotency.scopeKey";

    /** 请求属性：请求指纹。 */
    public static final String ATTR_FINGERPRINT = "vane.idempotency.fingerprint";

    /** 幂等键格式：8–128 位字母、数字与 `._:-`。 */
    private static final Pattern KEY_PATTERN = Pattern.compile("[A-Za-z0-9._:-]{8,128}");

    /** 不参与幂等的路径前缀：认证类请求与服务商回执不做重放。 */
    private static final List<String> EXCLUDED_PREFIXES = List.of("/api/v1/auth/", "/api/v1/public/sms/");

    private IdempotencySupport() {
    }

    /** 是否是参与幂等的创建类请求。 */
    public static boolean isEligible(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        String path = request.getRequestURI();
        if (path == null || !path.startsWith("/api/v1/")) {
            return false;
        }
        for (String excluded : EXCLUDED_PREFIXES) {
            if (path.startsWith(excluded)) {
                return false;
            }
        }
        String contentType = request.getContentType();
        if (contentType == null || !contentType.toLowerCase(java.util.Locale.ROOT).contains("json")) {
            // 文件上传等 multipart 请求体量大，不做整体缓存
            return false;
        }
        if ("true".equalsIgnoreCase(request.getHeader("X-Vane-Dry-Run"))) {
            // 演练请求不落库，也不应写幂等记录
            return false;
        }
        return request.getHeader(HEADER) != null;
    }

    /** 幂等键格式是否合法。 */
    public static boolean isValidKey(String key) {
        return key != null && KEY_PATTERN.matcher(key).matches();
    }

    /** 请求指纹：方法、路径、操作者与请求体的摘要。 */
    public static String fingerprint(String method, String path, String actor, byte[] body) {
        StringBuilder builder = new StringBuilder()
                .append(method).append('\n')
                .append(path).append('\n')
                .append(actor == null ? "" : actor).append('\n')
                .append(sha256Hex(body == null ? new byte[0] : body));
        return sha256Hex(builder.toString().getBytes(StandardCharsets.UTF_8));
    }

    /** SHA-256 十六进制摘要。 */
    public static String sha256Hex(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}
