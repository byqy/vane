package xyz.byqy.vane.iam.application;

import java.time.Instant;
import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import xyz.byqy.vane.iam.domain.IamLoginLog;
import xyz.byqy.vane.iam.domain.IamLoginLogRepository;

/**
 * 登录日志写入。必须使用独立事务：登录失败会回滚外层事务，日志不能跟着丢。
 */
@Service
public class LoginLogRecorder {

    private final IamLoginLogRepository logs;

    public LoginLogRecorder(IamLoginLogRepository logs) {
        this.logs = logs;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(UUID userId, String username, String result, String detail, String ip, String userAgent) {
        try {
            logs.save(IamLoginLog.create(UUID.randomUUID(), userId, username, result, detail, ip, userAgent,
                    MDC.get("traceId"), Instant.now()));
        } catch (RuntimeException ignored) {
            // 日志失败不影响登录流程
        }
    }
}
