package xyz.byqy.vane.system.application;

import java.util.List;
import java.util.Map;

/** 服务商配置视图：密钥类字段只回显掩码（ADR-022）。 */
public record SmsProviderView(String code, String type, String name, Map<String, String> config,
                              List<String> countries, List<String> optoutKeywords,
                              boolean enabled, boolean isDefault, String keyVersion) {
}
