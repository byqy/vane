package xyz.byqy.vane.iam.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import xyz.byqy.vane.common.error.ApiException;
import xyz.byqy.vane.common.error.CommonErrors;
import xyz.byqy.vane.iam.domain.IamMfaRecoveryCode;
import xyz.byqy.vane.iam.domain.IamMfaRecoveryCodeRepository;
import xyz.byqy.vane.iam.domain.IamMfaTotp;
import xyz.byqy.vane.iam.domain.IamMfaTotpRepository;
import xyz.byqy.vane.iam.domain.IamUser;
import xyz.byqy.vane.iam.domain.IamUserRepository;

/**
 * MFA（ADR-010）：TOTP 一期实现。
 * - 绑定需密码二次确认，密钥以 AES-256-GCM 加密入库
 * - 校验接受 TOTP（±1 窗口、防重放）或一次性恢复码
 * - 连续失败达阈值锁定
 */
@Service
public class MfaService {

    private static final String FAIL_KEY = "vane:mfa:fail:";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final IamUserRepository users;
    private final IamMfaTotpRepository totpRepository;
    private final IamMfaRecoveryCodeRepository recoveryRepository;
    private final MfaCrypto crypto;
    private final MfaChallengeStore challenges;
    private final LoginAttemptCounter attempts;
    private final AuthService authService;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final String issuer;
    private final int recoveryCodeCount;
    private final int maxAttempts;
    private final Duration lockDuration;

    public MfaService(IamUserRepository users,
                      IamMfaTotpRepository totpRepository,
                      IamMfaRecoveryCodeRepository recoveryRepository,
                      MfaCrypto crypto,
                      MfaChallengeStore challenges,
                      LoginAttemptCounter attempts,
                      AuthService authService,
                      PasswordEncoder passwordEncoder,
                      Clock clock,
                      @Value("${vane.auth.mfa.issuer:vane}") String issuer,
                      @Value("${vane.auth.mfa.recovery-code-count:10}") int recoveryCodeCount,
                      @Value("${vane.auth.login-max-attempts:5}") int maxAttempts,
                      @Value("${vane.auth.login-lock:PT15M}") Duration lockDuration) {
        this.users = users;
        this.totpRepository = totpRepository;
        this.recoveryRepository = recoveryRepository;
        this.crypto = crypto;
        this.challenges = challenges;
        this.attempts = attempts;
        this.authService = authService;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.issuer = issuer;
        this.recoveryCodeCount = recoveryCodeCount;
        this.maxAttempts = maxAttempts;
        this.lockDuration = lockDuration;
    }

    /** 绑定第一步：密码确认后生成密钥与 otpauth URI（尚未启用）。 */
    @Transactional
    public BindStart startBind(UUID userId, String password) {
        IamUser user = requireUser(userId);
        requirePassword(user, password);

        totpRepository.deleteByUserId(userId);
        recoveryRepository.deleteByUserId(userId);

        byte[] secret = Totp.newSecret();
        IamMfaTotp totp = IamMfaTotp.create(UUID.randomUUID(), userId,
                crypto.encrypt(secret), crypto.keyVersion());
        totpRepository.save(totp);

        return new BindStart(Totp.base32(secret), Totp.otpauthUri(issuer, user.getUsername(), secret));
    }

    /** 绑定第二步：校验一次验证码后启用，并返回一次性恢复码。 */
    @Transactional
    public List<String> confirmBind(UUID userId, String code) {
        IamUser user = requireUser(userId);
        IamMfaTotp totp = totpRepository.findByUserId(userId)
                .orElseThrow(() -> new ApiException(CommonErrors.AUTH_MFA_INVALID));
        if (totp.isConfirmed()) {
            throw new ApiException(CommonErrors.REQUEST_PRECONDITION_FAILED);
        }

        byte[] secret = crypto.decrypt(totp.getSecretCipher());
        Instant now = clock.instant();
        long step = Totp.verify(secret, code, now);
        if (step < 0) {
            throw new ApiException(CommonErrors.AUTH_MFA_INVALID);
        }
        totp.confirm(now);
        totp.markUsedStep(step, now);
        totpRepository.save(totp);

        user.enableMfa();
        users.save(user);
        return issueRecoveryCodes(userId, now);
    }

    /** 登录第二步：消费 mfaToken，校验 TOTP 或恢复码，成功后签发令牌。 */
    @Transactional
    public LoginResult verifyLogin(String mfaToken, String code, String userAgent, String ip) {
        UUID userId = challenges.consume(Tokens.sha256(mfaToken))
                .orElseThrow(() -> new ApiException(CommonErrors.AUTH_MFA_INVALID));

        String failKey = FAIL_KEY + userId;
        if (attempts.count(failKey) >= maxAttempts) {
            throw new ApiException(CommonErrors.AUTH_MFA_LOCKED);
        }

        IamMfaTotp totp = totpRepository.findByUserId(userId)
                .filter(IamMfaTotp::isConfirmed)
                .orElseThrow(() -> new ApiException(CommonErrors.AUTH_MFA_INVALID));

        Instant now = clock.instant();
        byte[] secret = crypto.decrypt(totp.getSecretCipher());
        long step = Totp.verify(secret, code, now);
        boolean ok = step >= 0 && (totp.getLastUsedStep() == null || step > totp.getLastUsedStep());

        if (!ok) {
            ok = consumeRecoveryCode(userId, code, now);
        }
        if (!ok) {
            long failures = attempts.increment(failKey, lockDuration);
            if (failures >= maxAttempts) {
                throw new ApiException(CommonErrors.AUTH_MFA_LOCKED);
            }
            throw new ApiException(CommonErrors.AUTH_MFA_INVALID);
        }

        if (step >= 0) {
            totp.markUsedStep(step, now);
            totpRepository.save(totp);
        }
        attempts.clear(failKey);
        return authService.issueFor(userId, "WEB", null, userAgent, ip);
    }

    @Transactional
    public void disable(UUID userId, String password) {
        IamUser user = requireUser(userId);
        requirePassword(user, password);
        user.disableMfa();
        users.save(user);
        totpRepository.deleteByUserId(userId);
        recoveryRepository.deleteByUserId(userId);
    }

    @Transactional(readOnly = true)
    public boolean enabled(UUID userId) {
        return requireUser(userId).isMfaEnabled();
    }

    public record BindStart(String secretBase32, String otpauthUri) {
    }

    private List<String> issueRecoveryCodes(UUID userId, Instant now) {
        recoveryRepository.deleteByUserId(userId);
        List<String> raw = new ArrayList<>();
        for (int i = 0; i < recoveryCodeCount; i++) {
            String code = randomCode();
            raw.add(code);
            recoveryRepository.save(new IamMfaRecoveryCode(UUID.randomUUID(), userId, sha256(code), now));
        }
        return raw;
    }

    private boolean consumeRecoveryCode(UUID userId, String code, Instant now) {
        if (code == null || code.isBlank()) {
            return false;
        }
        return recoveryRepository.findByUserIdAndCodeHashAndUsedAtIsNull(userId, sha256(code.trim()))
                .map(recovery -> {
                    recovery.markUsed(now);
                    recoveryRepository.save(recovery);
                    return true;
                })
                .orElse(false);
    }

    private IamUser requireUser(UUID userId) {
        return users.findById(userId)
                .filter(IamUser::isActive)
                .orElseThrow(() -> new ApiException(CommonErrors.AUTH_TOKEN_INVALID));
    }

    private void requirePassword(IamUser user, String password) {
        if (password == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new ApiException(CommonErrors.AUTH_LOGIN_FAILED);
        }
    }

    private String randomCode() {
        byte[] bytes = new byte[8];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes).toUpperCase();
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
}
