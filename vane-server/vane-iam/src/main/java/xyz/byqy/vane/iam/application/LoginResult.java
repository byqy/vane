package xyz.byqy.vane.iam.application;

import java.time.Instant;
import java.util.UUID;

/**
 * 登录结果：
 * - 正常登录：accessToken / refreshToken / refreshExpiresAt 有值，mfaToken 为 null
 * - 需要 MFA：mfaToken 有值，其余为 null（03 §14 两步登录）
 */
public record LoginResult(UUID userId, String accessToken, long accessExpiresInSeconds,
                          String refreshToken, Instant refreshExpiresAt, String mfaToken) {

    public boolean mfaRequired() {
        return mfaToken != null;
    }
}
