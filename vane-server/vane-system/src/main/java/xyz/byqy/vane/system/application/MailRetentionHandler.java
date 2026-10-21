package xyz.byqy.vane.system.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import xyz.byqy.vane.system.infrastructure.MailLogRepository;

/** 内置任务：按保留期清理邮件发送日志（ADR-024，默认 180 天）。 */
@Component
public class MailRetentionHandler implements JobHandler {

    private static final Logger log = LoggerFactory.getLogger(MailRetentionHandler.class);

    private final MailLogRepository logs;
    private final int retentionDays;

    public MailRetentionHandler(MailLogRepository logs,
                                @Value("${vane.mail.log-retention-days:180}") int retentionDays) {
        this.logs = logs;
        this.retentionDays = retentionDays;
    }

    @Override
    public String name() {
        return "mail-log-retention";
    }

    @Override
    public void run(String params) {
        int deleted = logs.deleteOlderThan(retentionDays);
        log.info("mail-log-retention removed {} rows older than {} days", deleted, retentionDays);
    }
}
