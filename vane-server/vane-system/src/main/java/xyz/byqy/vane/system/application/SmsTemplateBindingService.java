package xyz.byqy.vane.system.application;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.ObjectMapper;
import xyz.byqy.vane.common.error.ApiException;
import xyz.byqy.vane.common.error.CommonErrors;
import xyz.byqy.vane.common.i18n.MessageResolver;
import xyz.byqy.vane.system.domain.SysMessageTemplate;
import xyz.byqy.vane.system.domain.SysMessageTemplateRepository;
import xyz.byqy.vane.system.infrastructure.SmsBindingRepository;
import xyz.byqy.vane.system.infrastructure.SmsBindingRepository.BindingRow;
import xyz.byqy.vane.system.infrastructure.SmsProviderRepository;
import xyz.byqy.vane.system.infrastructure.SmsSignatureRepository;

/** 短信模板绑定（ADR-022）：站内模板 → 厂商模板码 + 签名 + 变量映射，按国家与语言整体替换。 */
@Service
public class SmsTemplateBindingService {

    private final SmsBindingRepository bindings;
    private final SysMessageTemplateRepository templates;
    private final SmsProviderRepository providers;
    private final SmsSignatureRepository signatures;
    private final MessageResolver messages;
    private final ObjectMapper json;
    private final Set<String> allowedCountries;

    public SmsTemplateBindingService(SmsBindingRepository bindings, SysMessageTemplateRepository templates,
                                     SmsProviderRepository providers, SmsSignatureRepository signatures,
                                     MessageResolver messages, ObjectMapper json,
                                     @Value("${vane.sms.allowed-countries:CN,HK,MO,TW,SG,MY,TH,VN,ID,PH}")
                                     String allowedCountries) {
        this.bindings = bindings;
        this.templates = templates;
        this.providers = providers;
        this.signatures = signatures;
        this.messages = messages;
        this.json = json;
        this.allowedCountries = new LinkedHashSet<>(Arrays.stream(allowedCountries.split(","))
                .map(String::strip).filter(value -> !value.isEmpty()).toList());
    }

    @Transactional(readOnly = true)
    public List<BindingRow> list(String providerCode) {
        return bindings.findAll(providerCode);
    }

    @Transactional(readOnly = true)
    public List<BindingRow> listByTemplate(UUID templateId) {
        return bindings.findByTemplate(templateId);
    }

    @Transactional
    public List<BindingRow> replace(UUID templateId, List<BindingSpec> specs) {
        SysMessageTemplate template = templates.findByIdAndDeletedAtIsNull(templateId)
                .filter(item -> "SMS".equals(item.getChannel()))
                .orElseThrow(() -> new ApiException(CommonErrors.SMS_TEMPLATE_NOT_BOUND));
        List<BindingRow> rows = (specs == null ? List.<BindingSpec>of() : specs).stream()
                .map(spec -> toRow(template, spec))
                .toList();
        bindings.replaceForTemplate(templateId, rows);
        return bindings.findByTemplate(templateId);
    }

    private BindingRow toRow(SysMessageTemplate template, BindingSpec spec) {
        if (spec.providerCode() == null || providers.findByCode(spec.providerCode()).isEmpty()) {
            throw new ApiException(CommonErrors.SMS_PROVIDER_NOT_FOUND);
        }
        if (spec.providerTemplateCode() == null || spec.providerTemplateCode().isBlank()
                || spec.providerTemplateCode().length() > 64) {
            throw new ApiException(CommonErrors.VALIDATION_FAILED);
        }
        if (spec.countryCode() == null || !allowedCountries.contains(spec.countryCode())) {
            throw new ApiException(CommonErrors.VALIDATION_FAILED);
        }
        if (spec.locale() == null || !messages.isSupportedLocale(spec.locale())) {
            throw new ApiException(CommonErrors.VALIDATION_FAILED);
        }
        if (spec.signatureId() != null) {
            var signature = signatures.findById(spec.signatureId())
                    .orElseThrow(() -> new ApiException(CommonErrors.SMS_SIGNATURE_NOT_FOUND));
            if (!signature.providerCode().equals(spec.providerCode())) {
                throw new ApiException(CommonErrors.VALIDATION_FAILED);
            }
        }
        Map<String, String> mapping = spec.paramMapping() == null ? Map.of() : spec.paramMapping();
        return new BindingRow(null, template.getId(), template.getCode(), spec.providerCode(),
                spec.providerTemplateCode(), spec.signatureId(), spec.countryCode(), spec.locale(),
                json.writeValueAsString(mapping));
    }

    public record BindingSpec(String providerCode, String providerTemplateCode, UUID signatureId,
                              String countryCode, String locale, Map<String, String> paramMapping) {
    }
}
