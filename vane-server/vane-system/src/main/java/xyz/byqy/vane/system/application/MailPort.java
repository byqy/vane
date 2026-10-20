package xyz.byqy.vane.system.application;

/** 邮件发送出口：实现可替换（SMTP / 日志降级 / 测试桩）。 */
public interface MailPort {

    void send(String to, String subject, String body);
}
