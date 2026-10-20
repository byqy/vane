package xyz.byqy.vane.api.auth;

/** 登录/校验响应：正常返回 access；需要 MFA 时返回一次性 mfaToken。 */
public record AuthResponse(String accessToken, String tokenType, Long expiresIn,
                           boolean mfaRequired, String mfaToken) {

    public static AuthResponse tokens(String accessToken, long expiresIn) {
        return new AuthResponse(accessToken, "Bearer", expiresIn, false, null);
    }

    public static AuthResponse mfaChallenge(String mfaToken) {
        return new AuthResponse(null, null, null, true, mfaToken);
    }
}
