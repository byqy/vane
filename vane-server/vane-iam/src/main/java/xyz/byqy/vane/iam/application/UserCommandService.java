package xyz.byqy.vane.iam.application;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import xyz.byqy.vane.common.audit.AuditContext;
import xyz.byqy.vane.common.error.ApiException;
import xyz.byqy.vane.common.error.CommonErrors;
import xyz.byqy.vane.iam.domain.IamUser;
import xyz.byqy.vane.iam.domain.IamUserRepository;

/** 用户写操作（ADM-01）。软删除保留登录名可复用（部分唯一索引只约束未删除行）。 */
@Service
public class UserCommandService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final IamUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final UserSummaryAssembler assembler;

    public UserCommandService(IamUserRepository users, PasswordEncoder passwordEncoder,
                              UserSummaryAssembler assembler) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.assembler = assembler;
    }

    @Transactional
    public UserSummary create(String username, String rawPassword, String nickname, UUID deptId) {
        if (users.existsByUsernameAndDeletedAtIsNull(username)) {
            throw new ApiException(CommonErrors.IAM_USER_USERNAME_TAKEN);
        }
        IamUser user = IamUser.create(UUID.randomUUID(), username, passwordEncoder.encode(rawPassword),
                deptId, "ACTIVE");
        user.updateProfile(nickname, deptId, "ACTIVE");
        users.save(user);
        return toSummary(user);
    }

    @Transactional(readOnly = true)
    public UserSummary get(UUID id) {
        return users.findById(id)
                .filter(IamUser::isNotDeleted)
                .map(this::toSummary)
                .orElseThrow(() -> new ApiException(CommonErrors.IAM_USER_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public long currentVersion(UUID id) {
        return users.findById(id).filter(IamUser::isNotDeleted).map(IamUser::getVersion)
                .orElseThrow(() -> new ApiException(CommonErrors.IAM_USER_NOT_FOUND));
    }

    @Transactional
    public UserSummary update(UUID id, long expectedVersion, String nickname, UUID deptId, String status) {
        IamUser user = users.findById(id)
                .filter(IamUser::isNotDeleted)
                .orElseThrow(() -> new ApiException(CommonErrors.IAM_USER_NOT_FOUND));
        if (user.getVersion() != expectedVersion) {
            throw new ApiException(CommonErrors.REQUEST_PRECONDITION_FAILED);
        }
        Map<String, Object> before = Map.of("nickname", String.valueOf(user.getNickname()),
                "deptId", String.valueOf(user.getDeptId()), "status", user.getStatus());
        user.updateProfile(nickname, deptId, status);
        users.save(user);
        Map<String, Object> after = Map.of("nickname", String.valueOf(user.getNickname()),
                "deptId", String.valueOf(user.getDeptId()), "status", user.getStatus());
        AuditContext.record(before, after, "nickname", "deptId", "status");
        return toSummary(user);
    }

    @Transactional
    public void delete(UUID id) {
        IamUser user = users.findById(id)
                .orElseThrow(() -> new ApiException(CommonErrors.IAM_USER_NOT_FOUND));
        AuditContext.recordDeleted(Map.of("username", user.getUsername(), "status", user.getStatus()),
                "username", "status");
        user.softDelete();
        users.save(user);
    }

    /** 重置密码：生成随机密码，仅本次返回。 */
    @Transactional
    public String resetPassword(UUID id) {
        IamUser user = users.findById(id)
                .filter(IamUser::isNotDeleted)
                .orElseThrow(() -> new ApiException(CommonErrors.IAM_USER_NOT_FOUND));
        byte[] bytes = new byte[12];
        RANDOM.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        user.changePassword(passwordEncoder.encode(raw));
        users.save(user);
        AuditContext.record(Map.of("passwordChanged", false), Map.of("passwordChanged", true),
                "passwordChanged");
        return raw;
    }

    private UserSummary toSummary(IamUser user) {
        return assembler.assemble(user);
    }
}



