package xyz.byqy.vane.common.web;

import java.security.SecureRandom;
import java.util.HexFormat;

import org.slf4j.MDC;

/** traceId 贯穿日志、响应头与错误体，形态为 32 位十六进制（与 W3C trace-id 一致）。 */
public final class TraceId {

    public static final String HEADER = "X-Request-Id";
    public static final String MDC_KEY = "traceId";
    public static final String REQUEST_ATTR = "xyz.byqy.vane.traceId";

    private static final SecureRandom RANDOM = new SecureRandom();

    private TraceId() {
    }

    public static String generate() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    public static String current() {
        String value = MDC.get(MDC_KEY);
        return value == null ? "" : value;
    }
}
