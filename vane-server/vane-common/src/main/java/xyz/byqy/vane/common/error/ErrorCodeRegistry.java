package xyz.byqy.vane.common.error;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 错误码注册表：保证同一 code 只映射一个状态与 type。
 * 各模块在静态初始化时注册自有错误码；重复注册在启动期即失败。
 */
public final class ErrorCodeRegistry {

    private static final Map<String, ErrorCode> BY_CODE = new ConcurrentHashMap<>();

    static {
        for (CommonErrors value : CommonErrors.values()) {
            register(value);
        }
    }

    private ErrorCodeRegistry() {
    }

    public static void register(ErrorCode errorCode) {
        ErrorCode previous = BY_CODE.putIfAbsent(errorCode.code(), errorCode);
        if (previous != null && previous != errorCode) {
            throw new IllegalStateException("Duplicated error code: " + errorCode.code());
        }
    }

    public static Optional<ErrorCode> find(String code) {
        return Optional.ofNullable(BY_CODE.get(code));
    }

    public static Collection<ErrorCode> all() {
        return BY_CODE.values();
    }
}
