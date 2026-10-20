package xyz.byqy.vane.system.application;

/**
 * 短信发送出口（ADR-022）：业务只依赖该接口，厂商 SDK 实现放基础设施层。
 * 未配置服务商时由 LoggingSmsPort 降级为日志，与邮件未配置 SMTP 的处理一致。
 */
public interface SmsPort {

    SmsSendResult send(SmsMessage message);
}
