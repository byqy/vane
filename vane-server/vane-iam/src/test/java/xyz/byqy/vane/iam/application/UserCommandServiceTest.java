package xyz.byqy.vane.iam.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import xyz.byqy.vane.common.error.ApiException;
import xyz.byqy.vane.common.error.CommonErrors;
import xyz.byqy.vane.iam.domain.IamUser;
import xyz.byqy.vane.iam.domain.IamUserRepository;

class UserCommandServiceTest {

    private static final UUID DEPT = UUID.randomUUID();

    private final IamUserRepository users = mock(IamUserRepository.class);
    private final PasswordEncoder encoder = new PasswordEncoder() {
        @Override
        public String encode(CharSequence rawPassword) {
            return "enc:" + rawPassword;
        }

        @Override
        public boolean matches(CharSequence rawPassword, String encodedPassword) {
            return ("enc:" + rawPassword).equals(encodedPassword);
        }
    };
    private final UserSummaryAssembler assembler = mock(UserSummaryAssembler.class);
    private final UserCommandService service = new UserCommandService(users, encoder, assembler);

    @BeforeEach
    void setUp() {
        when(users.save(any(IamUser.class))).thenAnswer(inv -> inv.getArgument(0));
        when(assembler.assemble(any(IamUser.class))).thenAnswer(inv -> {
            IamUser u = inv.getArgument(0);
            return new UserSummary(u.getId(), u.getUsername(), u.getNickname(), u.getDeptId(), "HQ",
                    u.getStatus(), java.util.List.of(), u.getCreatedAt(), u.getLastLoginAt(), u.getVersion());
        });
    }

    @Test
    void createRejectsDuplicateUsername() {
        when(users.existsByUsernameAndDeletedAtIsNull("bob")).thenReturn(true);

        assertThatThrownBy(() -> service.create("bob", "Secret123", "Bob", DEPT))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).errorCode())
                .isEqualTo(CommonErrors.IAM_USER_USERNAME_TAKEN);
    }

    @Test
    void createEncodesPasswordAndRequiresChange() {
        when(users.existsByUsernameAndDeletedAtIsNull("bob")).thenReturn(false);

        UserSummary summary = service.create("bob", "Secret123", "Bob", DEPT);

        assertThat(summary.username()).isEqualTo("bob");
        verify(users).save(any(IamUser.class));
    }

    @Test
    void updateFailsOnVersionMismatch() {
        IamUser user = IamUser.create(UUID.randomUUID(), "bob", "enc:x", DEPT, "ACTIVE");
        when(users.findById(user.getId())).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.update(user.getId(), 99L, "Bobby", null, null))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).errorCode())
                .isEqualTo(CommonErrors.REQUEST_PRECONDITION_FAILED);
    }

    @Test
    void updateChangesProfileAndStatus() {
        IamUser user = IamUser.create(UUID.randomUUID(), "bob", "enc:x", DEPT, "ACTIVE");
        when(users.findById(user.getId())).thenReturn(Optional.of(user));

        UserSummary summary = service.update(user.getId(), 0L, "Bobby", null, "DISABLED");

        assertThat(summary.nickname()).isEqualTo("Bobby");
        assertThat(summary.status()).isEqualTo("DISABLED");
    }

    @Test
    void deleteMarksSoftDeleted() {
        IamUser user = IamUser.create(UUID.randomUUID(), "bob", "enc:x", DEPT, "ACTIVE");
        when(users.findById(user.getId())).thenReturn(Optional.of(user));

        service.delete(user.getId());

        assertThat(user.isNotDeleted()).isFalse();
    }

    @Test
    void resetPasswordReturnsNewSecretAndForcesChange() {
        IamUser user = IamUser.create(UUID.randomUUID(), "bob", "enc:old", DEPT, "ACTIVE");
        when(users.findById(user.getId())).thenReturn(Optional.of(user));

        String raw = service.resetPassword(user.getId());

        assertThat(raw).isNotBlank();
        assertThat(user.getPasswordHash()).isEqualTo("enc:" + raw);
        assertThat(user.isMustChangePassword()).isTrue();
    }
}

