package xyz.byqy.vane.system.application;

/** 邮件发送端口（ADR-024）：由 worker 调用，实现按服务商配置构建 SMTP 连接。 */
public interface MailPort {

    /** 发送纯文本邮件；配置错误或网络失败抛 RuntimeException，由 worker 分类处理。 */
    void send(SmtpSettings settings, String to, String subject, String body);

    /** 连通性与认证校验（不发送邮件）。 */
    void verify(SmtpSettings settings);

    /** 服务商连接参数（密码为解密后的明文，只在发送瞬间存在）。 */
    record SmtpSettings(String host, int port, String tlsMode, String username, String password,
                        String fromAddress, String fromName, int timeoutMs) {
    }
}
