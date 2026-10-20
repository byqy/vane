package xyz.byqy.vane.system.application;

import java.util.Map;

/** 厂商发送适配器（ADR-022）：按服务商类型实现，VendorSmsPort 负责选择与解密配置。 */
public interface SmsVendorSender {

    /** 对应 sys_sms_provider.provider_type。 */
    String providerType();

    SmsSendResult send(Map<String, String> config, SmsMessage message);
}
