package xyz.byqy.vane.api.auth;

/** 登录/刷新响应：只返回 access；refresh 走 HttpOnly Cookie，不进响应体。 */
public record TokenResponse(String accessToken, String tokenType, long expiresIn) {
}
