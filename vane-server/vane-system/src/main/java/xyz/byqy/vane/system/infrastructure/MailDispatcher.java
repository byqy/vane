package xyz.byqy.vane.system.infrastructure;

import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;

import xyz.byqy.vane.system.application.MailPort;

/**
 * 邮件发送实现（ADR-024）：按服务商参数即时构建 {@link JavaMailSenderImpl}，
 * 不再读取 {@code spring.mail.*} 单例配置。
 */
@Component
public class MailDispatcher implements MailPort {

    private static final Logger log = LoggerFactory.getLogger(MailDispatcher.class);

    @Override
    public void send(SmtpSettings settings, String to, String subject, String body) {
        JavaMailSenderImpl sender = build(settings);
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from(settings));
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        sender.send(message);
        log.debug("mail sent via {} to {}", settings.host(), mask(to));
    }

    @Override
    public void verify(SmtpSettings settings) {
        try {
            build(settings).testConnection();
        } catch (jakarta.mail.MessagingException ex) {
            throw new IllegalStateException("SMTP connection check failed", ex);
        }
    }

    private JavaMailSenderImpl build(SmtpSettings settings) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(settings.host());
        sender.setPort(settings.port());
        sender.setDefaultEncoding("UTF-8");
        if (settings.username() != null && !settings.username().isBlank()) {
            sender.setUsername(settings.username());
            sender.setPassword(settings.password());
        }
        Properties props = sender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", String.valueOf(settings.username() != null && !settings.username().isBlank()));
        props.put("mail.smtp.connectiontimeout", String.valueOf(settings.timeoutMs()));
        props.put("mail.smtp.timeout", String.valueOf(settings.timeoutMs()));
        props.put("mail.smtp.writetimeout", String.valueOf(settings.timeoutMs()));
        if ("STARTTLS".equals(settings.tlsMode())) {
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.starttls.required", "true");
        } else if ("SSL".equals(settings.tlsMode())) {
            props.put("mail.smtp.ssl.enable", "true");
        }
        return sender;
    }

    private static String from(SmtpSettings settings) {
        return settings.fromName() == null || settings.fromName().isBlank()
                ? settings.fromAddress()
                : settings.fromName() + " <" + settings.fromAddress() + ">";
    }

    private static String mask(String address) {
        int at = address.indexOf('@');
        if (at <= 1) {
            return "***";
        }
        return address.charAt(0) + "***" + address.substring(at);
    }
}
