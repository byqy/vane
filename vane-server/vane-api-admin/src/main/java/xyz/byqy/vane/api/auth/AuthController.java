package xyz.byqy.vane.api.auth;

import java.time.Duration;
import java.time.Instant;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import xyz.byqy.vane.audit.api.Audit;
import xyz.byqy.vane.common.error.ApiException;
import xyz.byqy.vane.common.error.CommonErrors;
import xyz.byqy.vane.iam.api.security.AuthenticatedUser;
import xyz.byqy.vane.iam.application.AuthService;
import xyz.byqy.vane.api.auth.MfaDtos.BindConfirmRequest;
import xyz.byqy.vane.api.auth.MfaDtos.BindConfirmResponse;
import xyz.byqy.vane.api.auth.MfaDtos.BindRequest;
import xyz.byqy.vane.api.auth.MfaDtos.BindStartResponse;
import xyz.byqy.vane.api.auth.MfaDtos.DisableRequest;
import xyz.byqy.vane.api.auth.MfaDtos.StatusResponse;
import xyz.byqy.vane.api.auth.MfaDtos.VerifyRequest;
import xyz.byqy.vane.iam.application.MeQueryService;
import xyz.byqy.vane.iam.application.MfaService;
import xyz.byqy.vane.iam.application.MeView;
import xyz.byqy.vane.iam.application.LoginResult;
import xyz.byqy.vane.system.application.SysConfigService;

/**
 * 认证接口（03 §14）：/api/v1/auth/**。
 * refresh 只走 HttpOnly Refresh Cookie（SameSite=Strict + Path=/）：
 * 生产用 {@code __Host-REFRESH}（必须 Secure）；本地 http 开发用 {@code REFRESH}，
 * 因为浏览器会直接丢弃「带 __Host- 前缀但没有 Secure」的 Cookie。
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    static final String SECURE_REFRESH_COOKIE = "__Host-REFRESH";
    static final String DEV_REFRESH_COOKIE = "REFRESH";

    private final AuthService authService;
    private final MeQueryService meQueryService;
    private final MfaService mfaService;
    private final SysConfigService configService;
    private final boolean cookieSecure;

    public AuthController(AuthService authService,
                          MeQueryService meQueryService,
                          MfaService mfaService,
                          SysConfigService configService,
                          @org.springframework.beans.factory.annotation.Value("${vane.auth.cookie-secure:true}") boolean cookieSecure) {
        this.authService = authService;
        this.meQueryService = meQueryService;
        this.mfaService = mfaService;
        this.configService = configService;
        this.cookieSecure = cookieSecure;
    }

    /** 本人修改密码（首登强制改密也走这里）。密码最小长度取自系统参数。 */
    @PostMapping("/password")
    @Audit(module = "auth", action = "password", targetType = "user", targetId = "#user.id()", detail = true)
    public ResponseEntity<Void> changePassword(@AuthenticationPrincipal AuthenticatedUser user,
                                               @Valid @RequestBody ChangePasswordRequest request) {
        int minLength = configService.getInt("security.password.min-length", 6);
        authService.changePassword(user.id(), request.currentPassword(), request.newPassword(), minLength);
        return ResponseEntity.noContent().build();
    }

    public record ChangePasswordRequest(@jakarta.validation.constraints.NotBlank String currentPassword,
                                        @jakarta.validation.constraints.NotBlank String newPassword) {
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request,
                                              HttpServletRequest http) {
        LoginResult result = authService.login(request.username(), request.password(), "WEB",
                null, http.getHeader(HttpHeaders.USER_AGENT), clientIp(http));
        if (result.mfaRequired()) {
            return ResponseEntity.ok(AuthResponse.mfaChallenge(result.mfaToken()));
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie(result.refreshToken(), result.refreshExpiresAt()).toString())
                .body(AuthResponse.tokens(result.accessToken(), result.accessExpiresInSeconds()));
    }

    @PostMapping("/mfa/verify")
    public ResponseEntity<AuthResponse> verifyMfa(@Valid @RequestBody VerifyRequest request, HttpServletRequest http) {
        LoginResult result = mfaService.verifyLogin(request.mfaToken(), request.code(),
                http.getHeader(HttpHeaders.USER_AGENT), clientIp(http));
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie(result.refreshToken(), result.refreshExpiresAt()).toString())
                .body(AuthResponse.tokens(result.accessToken(), result.accessExpiresInSeconds()));
    }

    @PostMapping("/mfa/bind")
    public BindStartResponse startBind(@AuthenticationPrincipal AuthenticatedUser user,
                                       @Valid @RequestBody BindRequest request) {
        MfaService.BindStart start = mfaService.startBind(user.id(), request.password());
        return new BindStartResponse(start.secretBase32(), start.otpauthUri());
    }

    @PostMapping("/mfa/bind/confirm")
    @Audit(module = "auth", action = "mfa-confirm", targetType = "user", targetId = "#user.id()", detail = true)
    public BindConfirmResponse confirmBind(@AuthenticationPrincipal AuthenticatedUser user,
                                           @Valid @RequestBody BindConfirmRequest request) {
        return new BindConfirmResponse(mfaService.confirmBind(user.id(), request.code()));
    }

    @PostMapping("/mfa/disable")
    @Audit(module = "auth", action = "mfa-disable", targetType = "user", targetId = "#user.id()", detail = true)
    public ResponseEntity<Void> disableMfa(@AuthenticationPrincipal AuthenticatedUser user,
                                           @Valid @RequestBody DisableRequest request) {
        mfaService.disable(user.id(), request.password());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/mfa/status")
    public StatusResponse mfaStatus(@AuthenticationPrincipal AuthenticatedUser user) {
        return new StatusResponse(mfaService.enabled(user.id()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(HttpServletRequest http) {
        String refreshToken = readRefreshToken(http);
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new ApiException(CommonErrors.AUTH_TOKEN_MISSING);
        }
        LoginResult result = authService.refresh(refreshToken, http.getHeader(HttpHeaders.USER_AGENT), clientIp(http));
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie(result.refreshToken(), result.refreshExpiresAt()).toString())
                .body(new TokenResponse(result.accessToken(), "Bearer", result.accessExpiresInSeconds()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest http) {
        authService.logout(readRefreshToken(http));
        ResponseCookie cleared = ResponseCookie.from(refreshCookieName(), "")
                .httpOnly(true).secure(cookieSecure).sameSite("Strict").path("/").maxAge(0).build();
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cleared.toString()).build();
    }

    @GetMapping("/me")
    public MeResponse me(@AuthenticationPrincipal AuthenticatedUser user) {
        if (user == null) {
            throw new ApiException(CommonErrors.AUTH_TOKEN_MISSING);
        }
        MeView view = meQueryService.load(user.id());
        return new MeResponse(view.id(), view.username(), view.deptId(), view.mustChangePassword(), view.roles(),
                    view.permissions(),
                view.menus().stream()
                        .map(m -> new MenuItem(m.code(), m.parentCode(), m.nameKey(), m.path(),
                                m.component(), m.icon(), m.sortNo()))
                        .toList(),
                view.locale(), view.timezone());
    }

    /** 保存界面语言偏好：只影响后续渲染，不改变当前请求的语言。 */
    @PatchMapping("/me/preferences")
    public ResponseEntity<Void> updatePreferences(@AuthenticationPrincipal AuthenticatedUser user,
                                                  @Valid @RequestBody PreferencesRequest request) {
        meQueryService.updatePreferences(user.id(), request.locale(), request.timezone());
        return ResponseEntity.noContent().build();
    }

    public record PreferencesRequest(
            @jakarta.validation.constraints.Size(max = 16) String locale,
            @jakarta.validation.constraints.Size(max = 64) String timezone) {
    }

    /** 生产（Secure）用 __Host- 前缀；本地 http 降级为普通名称，否则浏览器拒收。 */
    private String refreshCookieName() {
        return cookieSecure ? SECURE_REFRESH_COOKIE : DEV_REFRESH_COOKIE;
    }

    /** 兼容两种名称：改配置后旧 Cookie 仍可被判读，便于平滑切换。 */
    private static String readRefreshToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        String legacy = null;
        for (Cookie cookie : cookies) {
            if (SECURE_REFRESH_COOKIE.equals(cookie.getName())) {
                return cookie.getValue();
            }
            if (DEV_REFRESH_COOKIE.equals(cookie.getName())) {
                legacy = cookie.getValue();
            }
        }
        return legacy;
    }

    private ResponseCookie refreshCookie(String token, Instant expiresAt) {
        Duration maxAge = Duration.between(Instant.now(), expiresAt);
        return ResponseCookie.from(refreshCookieName(), token)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Strict")
                .path("/")
                .maxAge(maxAge.isNegative() ? Duration.ZERO : maxAge)
                .build();
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}








