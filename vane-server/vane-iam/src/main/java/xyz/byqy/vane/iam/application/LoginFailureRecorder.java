package xyz.byqy.vane.iam.application;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import xyz.byqy.vane.iam.domain.IamUserRepository;

/**
 * 记录账号锁定状态。必须使用独立事务：
 * 登录失败后外层会抛异常回滚，若共用事务，锁定状态会被一起回滚（实测踩到）。
 */
@Service
public class LoginFailureRecorder {

    private final IamUserRepository users;

    public LoginFailureRecorder(IamUserRepository users) {
        this.users = users;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void persistLock(UUID userId, Instant until) {
        users.findById(userId).ifPresent(user -> {
            user.lockUntil(until);
            users.save(user);
        });
    }
}
