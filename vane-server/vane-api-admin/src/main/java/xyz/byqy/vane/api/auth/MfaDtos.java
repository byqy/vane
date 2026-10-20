package xyz.byqy.vane.api.auth;

import java.util.List;

import jakarta.validation.constraints.NotBlank;

/** MFA 相关请求与响应。 */
public final class MfaDtos {

    private MfaDtos() {
    }

    public record VerifyRequest(@NotBlank String mfaToken, @NotBlank String code) {
    }

    public record BindRequest(@NotBlank String password) {
    }

    public record BindConfirmRequest(@NotBlank String code) {
    }

    public record DisableRequest(@NotBlank String password) {
    }

    public record BindStartResponse(String secretBase32, String otpauthUri) {
    }

    public record BindConfirmResponse(List<String> recoveryCodes) {
    }

    public record StatusResponse(boolean enabled) {
    }
}
