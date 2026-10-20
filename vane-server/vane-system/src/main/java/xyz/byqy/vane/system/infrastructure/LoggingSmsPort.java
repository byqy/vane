package xyz.byqy.vane.system.infrastructure;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import xyz.byqy.vane.system.application.SmsMessage;
import xyz.byqy.vane.system.application.SmsPort;
import xyz.byqy.vane.system.application.SmsSendResult;

/** 未接入厂商 SDK 时（或 vane.sms.mode=logging）的降级实现：只记录日志，不真实发送。 */
@Component
@ConditionalOnProperty(name = "vane.sms.mode", havingValue = "logging", matchIfMissing = true)
public class LoggingSmsPort implements SmsPort {

    private static final Logger log = LoggerFactory.getLogger(LoggingSmsPort.class);

    @Override
    public SmsSendResult send(SmsMessage message) {
        log.info("[sms:logging] provider={} phone={} template={} signature={} variables={}",
                message.providerCode(), message.phoneE164(), message.providerTemplateCode(),
                message.signature(), message.variables());
        return SmsSendResult.ok("logging-" + UUID.randomUUID(), "LOGGED");
    }
}
