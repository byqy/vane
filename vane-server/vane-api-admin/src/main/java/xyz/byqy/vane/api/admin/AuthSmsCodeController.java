package xyz.byqy.vane.api.admin;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import xyz.byqy.vane.system.application.SmsVerificationService;

/** 短信验证码（ADR-022 §18.3）：匿名可用，按 IP/号码频控由服务层处理。 */
@RestController
@RequestMapping("/api/v1/auth/sms-code")
public class AuthSmsCodeController {

    private final SmsVerificationService verification;

    public AuthSmsCodeController(SmsVerificationService verification) {
        this.verification = verification;
    }

    @PostMapping("/send")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void send(@Valid @RequestBody SendRequest request) {
        verification.request(request.scene(), request.phone(), request.locale());
    }

    @PostMapping("/verify")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void verify(@Valid @RequestBody VerifyRequest request) {
        verification.verify(request.scene(), request.phone(), request.code());
    }

    public record SendRequest(@NotBlank String scene, @NotBlank String phone, String locale) {
    }

    public record VerifyRequest(@NotBlank String scene, @NotBlank String phone, @NotBlank String code) {
    }
}
