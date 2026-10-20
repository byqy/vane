package xyz.byqy.vane.system.infrastructure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import xyz.byqy.vane.system.application.MailPort;

/**
 * 邮件发送：配置了 spring.mail.host 时走 SMTP，否则降级为记录日志。
 * 降级不抛错——本地与演示环境没有 SMTP 也能把流程跑通。
 */
@Component
public class MailDispatcher implements MailPort {

    private static final Logger log = LoggerFactory.getLogger(MailDispatcher.class);

    private final ObjectProvider<JavaMailSender> mailSender;
    private final String host;
    private final String from;

    public MailDispatcher(ObjectProvider<JavaMailSender> mailSender,
                          @Value("${spring.mail.host:}") String host,
                          @Value("${vane.mail.from:no-reply@vane.local}") String from) {
        this.mailSender = mailSender;
        this.host = host;
        this.from = from;
    }

    @Override
    public void send(String to, String subject, String body) {
        JavaMailSender sender = mailSender.getIfAvailable();
        if (host == null || host.isBlank() || sender == null) {
            log.warn("SMTP is not configured, mail suppressed: to={} subject={}", to, subject);
            return;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        sender.send(message);
    }
}
