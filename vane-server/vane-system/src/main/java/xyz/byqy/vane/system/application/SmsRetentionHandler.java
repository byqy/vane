package xyz.byqy.vane.system.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** 内置任务：按保留期清理短信数据（ADR-022 P3）。 */
@Component
public class SmsRetentionHandler implements JobHandler {

    private static final Logger log = LoggerFactory.getLogger(SmsRetentionHandler.class);

    private final JdbcTemplate jdbc;
    private final int logDays;
    private final int recipientDays;
    private final int inboundDays;

    public SmsRetentionHandler(JdbcTemplate jdbc,
                               @Value("${vane.sms.retention.log-days:180}") int logDays,
                               @Value("${vane.sms.retention.recipient-days:90}") int recipientDays,
                               @Value("${vane.sms.retention.inbound-days:180}") int inboundDays) {
        this.jdbc = jdbc;
        this.logDays = logDays;
        this.recipientDays = recipientDays;
        this.inboundDays = inboundDays;
    }

    @Override
    public String name() {
        return "sms-retention";
    }

    @Override
    public void run(String params) {
        int logs = jdbc.update("""
                delete from sys_sms_log
                 where created_at < now() - make_interval(days => ?)
                """, logDays);
        int recipients = jdbc.update("""
                delete from sys_sms_recipient r
                 using sys_sms_campaign c
                 where r.campaign_id = c.id
                   and c.status in ('DONE', 'CANCELLED')
                   and r.created_at < now() - make_interval(days => ?)
                """, recipientDays);
        int inbounds = jdbc.update("""
                delete from sys_sms_inbound
                 where received_at < now() - make_interval(days => ?)
                """, inboundDays);
        log.info("SMS retention cleanup: logs={}, recipients={}, inbounds={}", logs, recipients, inbounds);
    }
}
