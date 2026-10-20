package xyz.byqy.vane.system.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import xyz.byqy.vane.common.error.ApiException;
import xyz.byqy.vane.common.error.CommonErrors;
import xyz.byqy.vane.system.domain.SysMessageTemplate;
import xyz.byqy.vane.system.domain.SysMessageTemplateRepository;
import xyz.byqy.vane.system.infrastructure.SmsBindingRepository;
import xyz.byqy.vane.system.infrastructure.SmsBindingRepository.BindingRow;
import xyz.byqy.vane.system.infrastructure.SmsLogRepository;
import xyz.byqy.vane.system.infrastructure.SmsLogRepository.SmsLogEntry;
import xyz.byqy.vane.system.infrastructure.SmsProviderRepository;
import xyz.byqy.vane.system.infrastructure.SmsProviderRepository.SmsProviderRow;
import xyz.byqy.vane.system.infrastructure.SmsSignatureRepository;

class SmsSendServiceTest {

    private final SysMessageTemplateRepository templates = mock(SysMessageTemplateRepository.class);
    private final SmsBindingRepository bindings = mock(SmsBindingRepository.class);
    private final SmsProviderRepository providers = mock(SmsProviderRepository.class);
    private final SmsSignatureRepository signatures = mock(SmsSignatureRepository.class);
    private final SmsPort smsPort = mock(SmsPort.class);
    private final SmsPhoneValidator phones = mock(SmsPhoneValidator.class);
    private final MessageTemplateService messageTemplates = mock(MessageTemplateService.class);
    private final SmsLogRepository logs = mock(SmsLogRepository.class);
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> valueOps = mock(ValueOperations.class);

    private final SmsSendService service = new SmsSendService(templates, bindings, providers, signatures, smsPort,
            phones, messageTemplates, logs, redis);

    private final UUID templateId = UUID.randomUUID();
    private final SysMessageTemplate template = mock(SysMessageTemplate.class);
    private final SmsProviderRow provider = new SmsProviderRow("aliyun-cn", "ALIYUN", "阿里云", "cipher", "1",
            "[\"CN\"]", "[]", true, true);
    private final BindingRow binding = new BindingRow(UUID.randomUUID(), templateId, "sms.verify", "aliyun-cn",
            "SMS_123", null, "CN", "zh-CN", "{}");

    private void givenHappyPath() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), eq("1"), any(Duration.class))).thenReturn(true);
        when(phones.normalize("+8613800138000"))
                .thenReturn(new SmsPhoneValidator.NormalizedPhone("+8613800138000", "CN"));
        when(templates.findByCodeAndChannelAndDeletedAtIsNull("sms.verify", "SMS"))
                .thenReturn(Optional.of(template));
        when(template.getId()).thenReturn(templateId);
        when(providers.findDefault()).thenReturn(Optional.of(provider));
        when(bindings.find(templateId, "aliyun-cn", "CN", "zh-CN")).thenReturn(Optional.of(binding));
        when(messageTemplates.render("sms.verify", "SMS", "zh-CN", Map.of("code", "123456")))
                .thenReturn(new MessageTemplateService.Rendered("sms.verify", "SMS", "zh-CN", null,
                        "您的验证码是 123456"));
        when(smsPort.send(any())).thenReturn(SmsSendResult.ok("msg-1", "OK"));
    }

    @Test
    void sendsRenderedSmsAndWritesLog() {
        givenHappyPath();

        SmsSendService.SendOutcome outcome = service.send("sms.verify", "+8613800138000", "zh-CN",
                Map.of("code", "123456"), "VERIFY_CODE");

        assertThat(outcome.providerCode()).isEqualTo("aliyun-cn");
        assertThat(outcome.providerMessageId()).isEqualTo("msg-1");
        assertThat(outcome.phoneMasked()).doesNotContain("13800138000");
        ArgumentCaptor<SmsLogEntry> log = ArgumentCaptor.forClass(SmsLogEntry.class);
        verify(logs).append(log.capture());
        assertThat(log.getValue().result()).isEqualTo("SUCCESS");
        assertThat(log.getValue().providerMessageId()).isEqualTo("msg-1");
        assertThat(log.getValue().phoneMasked()).doesNotContain("13800138000");
    }

    @Test
    void rejectsMissingVariablesBeforeCallingProvider() {
        givenHappyPath();
        when(messageTemplates.render("sms.verify", "SMS", "zh-CN", Map.of("code", "123456")))
                .thenReturn(new MessageTemplateService.Rendered("sms.verify", "SMS", "zh-CN", null,
                        "您的验证码是 {code}"));

        assertThatThrownBy(() -> service.send("sms.verify", "+8613800138000", "zh-CN",
                Map.of("code", "123456"), "VERIFY_CODE"))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).errorCode())
                .isEqualTo(CommonErrors.SMS_TEMPLATE_VARIABLE_MISSING);
        verify(smsPort, never()).send(any());
    }

    @Test
    void verifyCodeSceneIsRateLimited() {
        when(phones.normalize("+8613800138000"))
                .thenReturn(new SmsPhoneValidator.NormalizedPhone("+8613800138000", "CN"));
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), eq("1"), any(Duration.class))).thenReturn(false);

        assertThatThrownBy(() -> service.send("sms.verify", "+8613800138000", "zh-CN", Map.of(), "VERIFY_CODE"))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).errorCode())
                .isEqualTo(CommonErrors.SMS_RATE_LIMITED);
        verify(smsPort, never()).send(any());
    }

    @Test
    void failsWhenProviderRejectsMessage() {
        givenHappyPath();
        when(smsPort.send(any())).thenReturn(SmsSendResult.failed("isv.BUSINESS_LIMIT_CONTROL", "REJECTED"));

        assertThatThrownBy(() -> service.send("sms.verify", "+8613800138000", "zh-CN",
                Map.of("code", "123456"), "VERIFY_CODE"))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).errorCode())
                .isEqualTo(CommonErrors.SMS_SEND_FAILED);
        ArgumentCaptor<SmsLogEntry> log = ArgumentCaptor.forClass(SmsLogEntry.class);
        verify(logs).append(log.capture());
        assertThat(log.getValue().result()).isEqualTo("FAIL");
    }

    @Test
    void fallsBackToChineseTemplateBindingWhenLocaleMissing() {
        givenHappyPath();
        when(bindings.find(templateId, "aliyun-cn", "CN", "en")).thenReturn(Optional.empty());
        when(bindings.find(templateId, "aliyun-cn", "CN", "zh-CN")).thenReturn(Optional.of(binding));
        when(messageTemplates.render("sms.verify", "SMS", "en", Map.of("code", "123456")))
                .thenReturn(new MessageTemplateService.Rendered("sms.verify", "SMS", "en", null, "Your code is 123456"));

        service.send("sms.verify", "+8613800138000", "en", Map.of("code", "123456"), "VERIFY_CODE");

        verify(smsPort).send(any());
    }

    @Test
    void maskedPhoneNeverLeaksFullNumber() {
        assertThat(SmsSendService.mask("+8613800138000")).isEqualTo("+8613****8000");
        assertThat(SmsSendService.mask(null)).isEqualTo("****");
        assertThat(SmsSendService.mask("+123")).isEqualTo("****");
    }
}
