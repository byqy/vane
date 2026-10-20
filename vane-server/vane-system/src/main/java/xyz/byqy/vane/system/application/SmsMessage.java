package xyz.byqy.vane.system.application;

import java.util.Map;

/** 发送一条短信所需的全部信息（厂商无关）。 */
public record SmsMessage(String providerCode, String phoneE164, String countryCode, String locale,
                         String signature, String providerTemplateCode, Map<String, String> variables) {
}
