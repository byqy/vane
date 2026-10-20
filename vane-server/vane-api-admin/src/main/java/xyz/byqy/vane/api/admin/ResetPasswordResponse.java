package xyz.byqy.vane.api.admin;

/** 重置密码结果：仅本次返回明文，后续不可再查。 */
public record ResetPasswordResponse(String password) {
}
