package xyz.byqy.vane.iam.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import xyz.byqy.vane.common.error.ApiException;
import xyz.byqy.vane.common.error.CommonErrors;
import xyz.byqy.vane.iam.domain.IamRefreshToken;
import xyz.byqy.vane.iam.domain.IamRefreshTokenRepository;
import xyz.byqy.vane.iam.domain.IamUser;
import xyz.byqy.vane.iam.domain.IamUserRepository;
import xyz.byqy.vane.iam.infrastructure.InMemoryAccessTokenStore;
import xyz.byqy.vane.iam.infrastructure.InMemoryLoginAttemptCounter;

class AuthServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-18T00:00:00Z");
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID DEPT_ID = UUID.randomUUID();

    private final IamUserRepository users = mock(IamUserRepository.class);
    private final IamRefreshTokenRepository refreshTokens = mock(IamRefreshTokenRepository.class);
    private final InMemoryAccessTokenStore accessTokens = new InMemoryAccessTokenStore();
    private final InMemoryLoginAttemptCounter attempts = new InMemoryLoginAttemptCounter();
    private final LoginFailureRecorder failureRecorder = mock(LoginFailureRecorder.class);
    private final MfaChallengeStore mfaChallenges = mock(MfaChallengeStore.class);
    private final LoginLogRecorder loginLogs = mock(LoginLogRecorder.class);
    private final PasswordEncoder passwordEncoder = new PasswordEncoder() {
        @Override
        public String encode(CharSequence rawPassword) {
            return "enc:" + rawPassword;
        }

        @Override
        public boolean matches(CharSequence rawPassword, String encodedPassword) {
            return ("enc:" + rawPassword).equals(encodedPassword);
        }
    };

    private final AuthService service = new AuthService(users, refreshTokens, accessTokens, attempts, failureRecorder, mfaChallenges, loginLogs, passwordEncoder,
            Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofMinutes(15), Duration.ofDays(7), 5, Duration.ofMinutes(15),
            Duration.ofMinutes(5));

    private IamUser activeUser() {
        return IamUser.create(USER_ID, "admin", "enc:secret", DEPT_ID, "ACTIVE");
    }

    @BeforeEach
    void setUp() {
        when(users.save(any(IamUser.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(refreshTokens.save(any(IamRefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void loginIssuesAccessAndRefresh() {
        when(users.findByUsernameAndDeletedAtIsNull("admin")).thenReturn(Optional.of(activeUser()));

        LoginResult result = service.login("admin", "secret", "WEB", null, "junit", "127.0.0.1");

        assertThat(result.accessToken()).isNotBlank();
        assertThat(result.refreshToken()).isNotBlank();
        assertThat(result.accessExpiresInSeconds()).isEqualTo(900);
        assertThat(accessTokens.resolve(Tokens.sha256(result.accessToken()))).contains(USER_ID);
        verify(refreshTokens).save(any(IamRefreshToken.class));
        verify(loginLogs, atLeastOnce()).record(any(), anyString(), anyString(), any(), any(), any());
    }

    @Test
    void loginRejectsWrongPasswordWithSameCodeAsUnknownUser() {
        when(users.findByUsernameAndDeletedAtIsNull("admin")).thenReturn(Optional.of(activeUser()));

        assertThatThrownBy(() -> service.login("admin", "wrong", "WEB", null, null, null))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).errorCode())
                .isEqualTo(CommonErrors.AUTH_LOGIN_FAILED);

        assertThatThrownBy(() -> service.login("ghost", "secret", "WEB", null, null, null))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).errorCode())
                .isEqualTo(CommonErrors.AUTH_LOGIN_FAILED);
    }

    @Test
    void refreshRotatesAndMarksPreviousUsed() {
        String raw = "old-refresh";
        IamRefreshToken stored = new IamRefreshToken(UUID.randomUUID(), UUID.randomUUID(), USER_ID,
                Tokens.sha256(raw), "WEB", null, null, null, NOW, NOW.plus(Duration.ofDays(7)));
        when(refreshTokens.findByTokenHash(Tokens.sha256(raw))).thenReturn(Optional.of(stored));
        when(users.findById(USER_ID)).thenReturn(Optional.of(activeUser()));

        LoginResult result = service.refresh(raw, "junit", "127.0.0.1");

        assertThat(result.refreshToken()).isNotEqualTo(raw);
        assertThat(stored.getUsedAt()).isEqualTo(NOW);
        verify(refreshTokens).save(stored);
    }

    @Test
    void reusingRefreshRevokesWholeFamily() {
        String raw = "reused-refresh";
        UUID familyId = UUID.randomUUID();
        IamRefreshToken stored = new IamRefreshToken(UUID.randomUUID(), familyId, USER_ID,
                Tokens.sha256(raw), "WEB", null, null, null, NOW, NOW.plus(Duration.ofDays(7)));
        stored.markUsed(NOW.minusSeconds(10), UUID.randomUUID());
        when(refreshTokens.findByTokenHash(Tokens.sha256(raw))).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> service.refresh(raw, null, null))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).errorCode())
                .isEqualTo(CommonErrors.AUTH_TOKEN_REUSED);

        verify(refreshTokens).revokeFamily(familyId, NOW);
    }

    @Test
    void expiredRefreshIsRejected() {
        String raw = "expired-refresh";
        IamRefreshToken stored = new IamRefreshToken(UUID.randomUUID(), UUID.randomUUID(), USER_ID,
                Tokens.sha256(raw), "WEB", null, null, null, NOW.minus(Duration.ofDays(8)), NOW.minusSeconds(1));
        when(refreshTokens.findByTokenHash(Tokens.sha256(raw))).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> service.refresh(raw, null, null))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).errorCode())
                .isEqualTo(CommonErrors.AUTH_TOKEN_EXPIRED);

        verify(users, never()).findById(any());
    }

    @Test
    void authenticateReturnsEmptyForUnknownToken() {
        assertThat(service.authenticate("no-such-token")).isEmpty();
    }

    @Test
    void repeatedFailuresLockAccount() {
        when(users.findByUsernameAndDeletedAtIsNull("admin")).thenReturn(Optional.of(activeUser()));

        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> service.login("admin", "wrong", "WEB", null, null, "10.0.0.1"))
                    .isInstanceOf(ApiException.class)
                    .extracting(ex -> ((ApiException) ex).errorCode())
                    .isEqualTo(CommonErrors.AUTH_LOGIN_FAILED);
        }

        assertThatThrownBy(() -> service.login("admin", "secret", "WEB", null, null, "10.0.0.1"))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).errorCode())
                .isEqualTo(CommonErrors.AUTH_ACCOUNT_LOCKED);
    }

    @Test
    void successfulLoginClearsFailureCounter() {
        when(users.findByUsernameAndDeletedAtIsNull("admin")).thenReturn(Optional.of(activeUser()));

        assertThatThrownBy(() -> service.login("admin", "wrong", "WEB", null, null, "10.0.0.2"))
                .isInstanceOf(ApiException.class);

        service.login("admin", "secret", "WEB", null, null, "10.0.0.2");

        assertThat(attempts.count(AuthService.FAIL_USER_KEY + "admin")).isZero();
        assertThat(attempts.count(AuthService.FAIL_IP_KEY + "10.0.0.2")).isZero();
    }

    @Test
    void mfaEnabledUserGetsChallengeInsteadOfTokens() {
        IamUser user = activeUser();
        user.enableMfa();
        when(users.findByUsernameAndDeletedAtIsNull("admin")).thenReturn(Optional.of(user));

        LoginResult result = service.login("admin", "secret", "WEB", null, null, null);

        assertThat(result.mfaRequired()).isTrue();
        assertThat(result.accessToken()).isNull();
        assertThat(result.mfaToken()).isNotBlank();
        verify(mfaChallenges).store(anyString(), any(), any());
    }

    @Test
    void logoutRevokesFamily() {
        String raw = "logout-refresh";
        UUID familyId = UUID.randomUUID();
        IamRefreshToken stored = new IamRefreshToken(UUID.randomUUID(), familyId, USER_ID,
                Tokens.sha256(raw), "WEB", null, null, null, NOW, NOW.plus(Duration.ofDays(7)));
        when(refreshTokens.findByTokenHash(anyString())).thenReturn(Optional.of(stored));

        service.logout(raw);

        verify(refreshTokens).revokeFamily(familyId, NOW);
    }
}





