package xyz.byqy.vane.iam.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import xyz.byqy.vane.common.error.ApiException;
import xyz.byqy.vane.common.error.CommonErrors;
import xyz.byqy.vane.iam.domain.IamRefreshToken;
import xyz.byqy.vane.iam.domain.IamRefreshTokenRepository;
import xyz.byqy.vane.iam.domain.IamUser;
import xyz.byqy.vane.iam.domain.IamUserRepository;

/**
 * 认证用例（03 §14、ADR-007、01 §10）：
 * - access：不透明令牌，15 分钟，服务端存哈希（Valkey）
 * - refresh：7 天，一次性轮换；已用过的令牌再次出现即判定泄露，撤销整族
 * - 登录失败按账号 + IP 双维度计数，达到阈值锁定
 */
@Service
public class AuthService {

    static final String FAIL_USER_KEY = "vane:login:fail:user:";
    static final String FAIL_IP_KEY = "vane:login:fail:ip:";

    private final IamUserRepository users;
    private final IamRefreshTokenRepository refreshTokens;
    private final AccessTokenStore accessTokens;
    private final LoginAttemptCounter attempts;
    private final LoginFailureRecorder failureRecorder;
    private final MfaChallengeStore mfaChallenges;
    private final LoginLogRecorder loginLogs;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final Duration accessTtl;
    private final Duration refreshTtl;
    private final int maxLoginAttempts;
    private final Duration loginLock;
    private final Duration mfaChallengeTtl;

    public AuthService(IamUserRepository users,
                       IamRefreshTokenRepository refreshTokens,
                       AccessTokenStore accessTokens,
                       LoginAttemptCounter attempts,
                       LoginFailureRecorder failureRecorder,
                       MfaChallengeStore mfaChallenges,
                       LoginLogRecorder loginLogs,
                       PasswordEncoder passwordEncoder,
                       Clock clock,
                       @Value("${vane.auth.access-ttl:PT15M}") Duration accessTtl,
                       @Value("${vane.auth.refresh-ttl:P7D}") Duration refreshTtl,
                       @Value("${vane.auth.login-max-attempts:5}") int maxLoginAttempts,
                       @Value("${vane.auth.login-lock:PT15M}") Duration loginLock,
                       @Value("${vane.auth.mfa.challenge-ttl:PT5M}") Duration mfaChallengeTtl) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.accessTokens = accessTokens;
        this.attempts = attempts;
        this.failureRecorder = failureRecorder;
        this.mfaChallenges = mfaChallenges;
        this.loginLogs = loginLogs;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.accessTtl = accessTtl;
        this.refreshTtl = refreshTtl;
        this.maxLoginAttempts = maxLoginAttempts;
        this.loginLock = loginLock;
        this.mfaChallengeTtl = mfaChallengeTtl;
    }

    @Transactional
    public LoginResult login(String username, String password, String clientType, String deviceId,
                             String userAgent, String ip) {
        String userKey = FAIL_USER_KEY + username;
        String ipKey = FAIL_IP_KEY + (ip == null ? "-" : ip);
        if (attempts.count(userKey) >= maxLoginAttempts || attempts.count(ipKey) >= maxLoginAttempts) {
            loginLogs.record(null, username, "FAIL_LOCKED", "too many attempts", ip, userAgent);
            throw new ApiException(CommonErrors.AUTH_ACCOUNT_LOCKED);
        }

        Instant now = clock.instant();
        IamUser user = users.findByUsernameAndDeletedAtIsNull(username).orElse(null);
        if (user == null || !user.isActive()) {
            registerFailure(userKey, ipKey, null, now);
            loginLogs.record(null, username, "FAIL_PASSWORD", "unknown or inactive user", ip, userAgent);
            throw new ApiException(CommonErrors.AUTH_LOGIN_FAILED);
        }
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(now)) {
            loginLogs.record(user.getId(), username, "FAIL_LOCKED", "account locked", ip, userAgent);
            throw new ApiException(CommonErrors.AUTH_ACCOUNT_LOCKED);
        }
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            registerFailure(userKey, ipKey, user, now);
            loginLogs.record(user.getId(), username, "FAIL_PASSWORD", "bad password", ip, userAgent);
            throw new ApiException(CommonErrors.AUTH_LOGIN_FAILED);
        }

        attempts.clear(userKey);
        attempts.clear(ipKey);
        if (user.getLockedUntil() != null) {
            user.clearLock();
        }
        user.markLoggedIn(now);
        users.save(user);

        loginLogs.record(user.getId(), username, "SUCCESS", null, ip, userAgent);
        if (user.isMfaEnabled()) {
            String challenge = Tokens.newOpaqueToken();
            mfaChallenges.store(Tokens.sha256(challenge), user.getId(), mfaChallengeTtl);
            return new LoginResult(user.getId(), null, 0, null, null, challenge);
        }
        return issue(user, UUID.randomUUID(), clientType, deviceId, userAgent, ip, now);
    }

    @Transactional
    public LoginResult refresh(String rawRefreshToken, String userAgent, String ip) {
        String hash = Tokens.sha256(rawRefreshToken);
        IamRefreshToken stored = refreshTokens.findByTokenHash(hash)
                .orElseThrow(() -> new ApiException(CommonErrors.AUTH_TOKEN_INVALID));
        Instant now = clock.instant();

        if (stored.getUsedAt() != null) {
            refreshTokens.revokeFamily(stored.getFamilyId(), now);
            throw new ApiException(CommonErrors.AUTH_TOKEN_REUSED);
        }
        if (stored.getRevokedAt() != null) {
            throw new ApiException(CommonErrors.AUTH_TOKEN_REVOKED);
        }
        if (!stored.isUsable(now)) {
            throw new ApiException(CommonErrors.AUTH_TOKEN_EXPIRED);
        }

        IamUser user = users.findById(stored.getUserId())
                .filter(IamUser::isActive)
                .orElseThrow(() -> new ApiException(CommonErrors.AUTH_TOKEN_INVALID));

        return issue(user, stored.getFamilyId(), stored.getClientType(), stored.getDeviceId(),
                userAgent != null ? userAgent : stored.getUserAgent(),
                ip != null ? ip : stored.getIp(), now, stored);
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }
        refreshTokens.findByTokenHash(Tokens.sha256(rawRefreshToken))
                .ifPresent(token -> refreshTokens.revokeFamily(token.getFamilyId(), clock.instant()));
    }

    /** 本人修改密码：校验当前密码与策略，成功后清除强制改密标记。 */
    @Transactional
    public void changePassword(UUID userId, String currentPassword, String newPassword, int minLength) {
        IamUser user = users.findById(userId)
                .filter(IamUser::isNotDeleted)
                .orElseThrow(() -> new ApiException(CommonErrors.AUTH_TOKEN_INVALID));

        if (currentPassword == null || !passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new ApiException(CommonErrors.AUTH_PASSWORD_MISMATCH);
        }
        if (newPassword == null || newPassword.length() < minLength) {
            throw new ApiException(CommonErrors.AUTH_PASSWORD_POLICY);
        }
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new ApiException(CommonErrors.AUTH_PASSWORD_UNCHANGED);
        }

        user.changePasswordBySelf(passwordEncoder.encode(newPassword));
        users.save(user);
    }

    /** 供 Bearer 过滤器调用：解析有效 access 令牌并加载用户。 */
    public Optional<IamUser> authenticate(String rawAccessToken) {
        if (rawAccessToken == null || rawAccessToken.isBlank()) {
            return Optional.empty();
        }
        return accessTokens.resolve(Tokens.sha256(rawAccessToken))
                .flatMap(users::findById)
                .filter(IamUser::isActive);
    }

    /** MFA 校验通过后签发令牌。 */
    @Transactional
    public LoginResult issueFor(UUID userId, String clientType, String deviceId, String userAgent, String ip) {
        var user = users.findById(userId)
                .filter(IamUser::isActive)
                .orElseThrow(() -> new ApiException(CommonErrors.AUTH_TOKEN_INVALID));
        return issue(user, UUID.randomUUID(), clientType, deviceId, userAgent, ip, clock.instant());
    }

    public Duration accessTtl() {
        return accessTtl;
    }

    public Duration refreshTtl() {
        return refreshTtl;
    }

    private void registerFailure(String userKey, String ipKey, IamUser user, Instant now) {
        long userCount = attempts.increment(userKey, loginLock);
        attempts.increment(ipKey, loginLock);
        if (userCount >= maxLoginAttempts && user != null) {
            failureRecorder.persistLock(user.getId(), now.plus(loginLock));
        }
    }

    private LoginResult issue(IamUser user, UUID familyId, String clientType, String deviceId,
                              String userAgent, String ip, Instant now) {
        return issue(user, familyId, clientType, deviceId, userAgent, ip, now, null);
    }

    private LoginResult issue(IamUser user, UUID familyId, String clientType, String deviceId,
                              String userAgent, String ip, Instant now, IamRefreshToken previous) {
        String access = Tokens.newOpaqueToken();
        accessTokens.store(Tokens.sha256(access), user.getId(), accessTtl);

        String refresh = Tokens.newOpaqueToken();
        IamRefreshToken next = new IamRefreshToken(UUID.randomUUID(), familyId, user.getId(),
                Tokens.sha256(refresh), clientType, deviceId, userAgent, ip, now, now.plus(refreshTtl));
        refreshTokens.save(next);
        if (previous != null) {
            previous.markUsed(now, next.getId());
            refreshTokens.save(previous);
        }
        return new LoginResult(user.getId(), access, accessTtl.toSeconds(), refresh, next.getExpiresAt(), null);
    }
}




