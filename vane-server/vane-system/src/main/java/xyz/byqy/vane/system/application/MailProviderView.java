package xyz.byqy.vane.system.application;

import xyz.byqy.vane.system.infrastructure.MailProviderRepository.MailProviderRow;

/** 邮件服务商视图：密码恒为掩码，不返回密文（ADR-024）。 */
public record MailProviderView(String code, String name, String host, int port, String tlsMode,
                               String username, String password, String fromAddress, String fromName,
                               boolean enabled, boolean isDefault, int dailyLimit, int timeoutMs) {

    public static MailProviderView of(MailProviderRow row) {
        return new MailProviderView(row.code(), row.name(), row.host(), row.port(), row.tlsMode(),
                row.username(), row.passwordCipher() == null ? null : "***", row.fromAddress(),
                row.fromName(), row.enabled(), row.isDefault(), row.dailyLimit(), row.timeoutMs());
    }

    public record Command(String code, String name, String host, int port, String tlsMode, String username,
                          String password, String fromAddress, String fromName, boolean enabled,
                          boolean isDefault, int dailyLimit, int timeoutMs) {
    }
}
