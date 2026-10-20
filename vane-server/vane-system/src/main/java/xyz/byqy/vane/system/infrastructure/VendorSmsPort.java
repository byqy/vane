package xyz.byqy.vane.system.infrastructure;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import tools.jackson.databind.ObjectMapper;
import xyz.byqy.vane.system.application.SmsMessage;
import xyz.byqy.vane.system.application.SmsPort;
import xyz.byqy.vane.system.application.SmsSendResult;
import xyz.byqy.vane.system.application.SmsVendorSender;
import xyz.byqy.vane.system.infrastructure.SmsProviderRepository.SmsProviderRow;

/**
 * 厂商发送路由（ADR-022）：按 provider_code 取配置、解密后交给对应厂商适配器。
 * 仅当 {@code vane.sms.mode=vendor} 时生效，默认仍用 {@link LoggingSmsPort}。
 */
@Component
@ConditionalOnProperty(prefix = "vane.sms", name = "mode", havingValue = "vendor")
public class VendorSmsPort implements SmsPort {

    private final SmsProviderRepository providers;
    private final SmsConfigCipher cipher;
    private final ObjectMapper json;
    private final Map<String, SmsVendorSender> senders = new HashMap<>();

    public VendorSmsPort(SmsProviderRepository providers, SmsConfigCipher cipher, ObjectMapper json,
                         List<SmsVendorSender> senders) {
        this.providers = providers;
        this.cipher = cipher;
        this.json = json;
        for (SmsVendorSender sender : senders) {
            this.senders.put(sender.providerType(), sender);
        }
    }

    @Override
    public SmsSendResult send(SmsMessage message) {
        SmsProviderRow provider = providers.findByCode(message.providerCode()).orElse(null);
        if (provider == null) {
            return SmsSendResult.failed("SMS.PROVIDER.NOT_FOUND", null);
        }
        SmsVendorSender sender = senders.get(provider.type());
        if (sender == null) {
            return SmsSendResult.failed("SMS.VENDOR.UNSUPPORTED", provider.type());
        }
        Map<String, String> config;
        try {
            @SuppressWarnings("unchecked")
            Map<String, String> decrypted = json.readValue(
                    cipher.decrypt(provider.code(), provider.keyVersion(), provider.configCipher()), Map.class);
            config = decrypted;
        } catch (RuntimeException ex) {
            return SmsSendResult.failed("SMS.CONFIG.DECRYPT_FAILED", ex.getMessage());
        }
        return sender.send(config, message);
    }
}
