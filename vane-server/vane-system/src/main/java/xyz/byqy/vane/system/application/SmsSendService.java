package xyz.byqy.vane.system.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import xyz.byqy.vane.common.error.ApiException;
import xyz.byqy.vane.common.error.CommonErrors;
import xyz.byqy.vane.common.web.TraceId;
import xyz.byqy.vane.system.domain.SysMessageTemplate;
import xyz.byqy.vane.system.domain.SysMessageTemplateRepository;
import xyz.byqy.vane.system.infrastructure.SmsBindingRepository;
import xyz.byqy.vane.system.infrastructure.SmsBindingRepository.BindingRow;
import xyz.byqy.vane.system.infrastructure.SmsLogRepository;
import xyz.byqy.vane.system.infrastructure.SmsLogRepository.SmsLogEntry;
import xyz.byqy.vane.system.infrastructure.SmsProviderRepository;
import xyz.byqy.vane.system.infrastructure.SmsProviderRepository.SmsProviderRow;
import xyz.byqy.vane.system.infrastructure.SmsSignatureRepository;

/**
 * 短信发送主链路（ADR-022）：号码规范化 → 场景频控 → 解析默认服务商与模板绑定 →
 * 渲染文案并校验变量 → 调用 {@link SmsPort} → 写发送日志（只增）。
 */
@Service
public class SmsSendService {

    private static final Pattern REMAINING_VARIABLE = Pattern.compile("\\{[a-zA-Z0-9_]+}");
    private static final String SMS_CHANNEL = "SMS";
    private static final String FALLBACK_LOCALE = "zh-CN";
    private static final Duration VERIFY_CODE_COOLDOWN = Duration.ofSeconds(60);

    private final SysMessageTemplateRepository templates;
    private final SmsBindingRepository bindings;
    private final SmsProviderRepository providers;
    private final SmsSignatureRepository signatures;
    private final SmsPort smsPort;
    private final SmsPhoneValidator phones;
    private final MessageTemplateService messageTemplates;
    private final SmsLogRepository logs;
    private final StringRedisTemplate redis;

    public SmsSendService(SysMessageTemplateRepository templates, SmsBindingRepository bindings,
                          SmsProviderRepository providers, SmsSignatureRepository signatures, SmsPort smsPort,
                          SmsPhoneValidator phones, MessageTemplateService messageTemplates,
                          SmsLogRepository logs, StringRedisTemplate redis) {
        this.templates = templates;
        this.bindings = bindings;
        this.providers = providers;
        this.signatures = signatures;
        this.smsPort = smsPort;
        this.phones = phones;
        this.messageTemplates = messageTemplates;
        this.logs = logs;
        this.redis = redis;
    }

    /** 场景取值：`VERIFY_CODE` 走 60 秒冷却，其它场景按业务侧频控处理。 */
    @Transactional
    public SendOutcome send(String templateCode, String phone, String locale, Map<String, String> variables,
                            String scene) {
        return send(templateCode, phone, locale, variables, scene, null);
    }

    /** 群发场景使用：把 campaign_id 一并写入发送日志。 */
    @Transactional
    public SendOutcome send(String templateCode, String phone, String locale, Map<String, String> variables,
                            String scene, UUID campaignId) {
        SmsPhoneValidator.NormalizedPhone normalized = phones.normalize(phone);
        String phoneHash = sha256(normalized.e164());
        applyRateLimit(scene, phoneHash);

        SysMessageTemplate template = templates
                .findByCodeAndChannelAndDeletedAtIsNull(templateCode, SMS_CHANNEL)
                .orElseThrow(() -> new ApiException(CommonErrors.SMS_TEMPLATE_NOT_BOUND));
        SmsProviderRow provider = providers.findDefault()
                .orElseThrow(() -> new ApiException(CommonErrors.SMS_NOT_CONFIGURED));
        BindingRow binding = bindings
                .find(template.getId(), provider.code(), normalized.countryCode(), locale)
                .or(() -> bindings.find(template.getId(), provider.code(), normalized.countryCode(), FALLBACK_LOCALE))
                .orElseThrow(() -> new ApiException(CommonErrors.SMS_TEMPLATE_NOT_BOUND));

        MessageTemplateService.Rendered rendered = messageTemplates.render(templateCode, SMS_CHANNEL, locale, variables);
        if (rendered.body() != null && REMAINING_VARIABLE.matcher(rendered.body()).find()) {
            throw new ApiException(CommonErrors.SMS_TEMPLATE_VARIABLE_MISSING);
        }
        String signature = binding.signatureId() == null ? null
                : signatures.findSignName(binding.signatureId()).orElse(null);

        long started = System.nanoTime();
        try {
            SmsSendResult result = smsPort.send(new SmsMessage(provider.code(), normalized.e164(),
                    normalized.countryCode(), locale, signature, binding.providerTemplateCode(),
                    variables == null ? Map.of() : variables));
            int durationMs = (int) ((System.nanoTime() - started) / 1_000_000);
            logs.append(new SmsLogEntry(templateCode, mask(normalized.e164()), phoneHash,
                    normalized.countryCode(), provider.code(), result.providerMessageId(), campaignId,
                    result.success() ? "SUCCESS" : "FAIL", result.errorCode(), durationMs, TraceId.current()));
            if (!result.success()) {
                throw new ApiException(CommonErrors.SMS_SEND_FAILED);
            }
            return new SendOutcome(provider.code(), result.providerMessageId(), mask(normalized.e164()),
                    result.providerStatus());
        } catch (ApiException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            int durationMs = (int) ((System.nanoTime() - started) / 1_000_000);
            logs.append(new SmsLogEntry(templateCode, mask(normalized.e164()), phoneHash,
                    normalized.countryCode(), provider.code(), null, campaignId, "FAIL",
                    CommonErrors.SMS_SEND_FAILED.code(), durationMs, TraceId.current()));
            throw new ApiException(CommonErrors.SMS_SEND_FAILED);
        }
    }

    private void applyRateLimit(String scene, String phoneHash) {
        if (!"VERIFY_CODE".equals(scene)) {
            return;
        }
        String key = "vane:sms:rate:verify:" + phoneHash;
        Boolean acquired = redis.opsForValue().setIfAbsent(key, "1", VERIFY_CODE_COOLDOWN);
        if (Boolean.FALSE.equals(acquired)) {
            throw new ApiException(CommonErrors.SMS_RATE_LIMITED);
        }
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    /** 掩码保留国家码与首尾片段，便于排查且不落明文。 */
    static String mask(String e164) {
        if (e164 == null || e164.length() <= 7) {
            return "****";
        }
        int prefix = Math.min(5, e164.length() - 4);
        return e164.substring(0, prefix) + "****" + e164.substring(e164.length() - 4);
    }

    public record SendOutcome(String providerCode, String providerMessageId, String phoneMasked,
                              String providerStatus) {
    }
}
