package xyz.byqy.vane.common.web;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import xyz.byqy.vane.common.error.ApiException;
import xyz.byqy.vane.common.error.CommonErrors;

/**
 * 游标分页的游标编解码（docs/03 §9）。
 *
 * <p>游标由「排序键时间 + 主键」组成，不透明、可逆，客户端不得解析。
 * 排序键保留完整精度（数据库存到微秒，截断到毫秒会让边界记录重复出现）。</p>
 */
public final class CursorToken {

    /** 游标指向的位置：按 (at, id) 排序。 */
    public record Key(Instant at, UUID id) {
    }

    private CursorToken() {
    }

    public static String encode(Instant at, UUID id) {
        return encodeParts(at.toString(), id.toString());
    }

    /** 通用编码：把排序键各段拼起来（各段不得含 `|`）。 */
    public static String encodeParts(String... parts) {
        String raw = String.join("|", parts);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /** 通用解码；格式非法抛 {@link CommonErrors#REQUEST_MALFORMED}。 */
    public static String[] decodeParts(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            String raw = new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
            String[] parts = raw.split("\\|");
            if (parts.length == 0) {
                throw new IllegalArgumentException("空游标");
            }
            return parts;
        } catch (RuntimeException e) {
            throw new ApiException(CommonErrors.REQUEST_MALFORMED, "游标无效，请从上一页响应的 nextAfter 取值");
        }
    }

    /** 解析游标；格式非法时抛出 {@link CommonErrors#REQUEST_MALFORMED}。 */
    public static Key decode(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            String raw = new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
            int split = raw.indexOf('|');
            if (split <= 0) {
                throw new IllegalArgumentException("缺少分隔符");
            }
            return new Key(Instant.parse(raw.substring(0, split)), UUID.fromString(raw.substring(split + 1)));
        } catch (RuntimeException e) {
            throw new ApiException(CommonErrors.REQUEST_MALFORMED, "游标无效，请从上一页响应的 nextAfter 取值");
        }
    }
}
