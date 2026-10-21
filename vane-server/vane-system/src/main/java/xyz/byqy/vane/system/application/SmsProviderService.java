package xyz.byqy.vane.system.application;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import xyz.byqy.vane.common.audit.AuditContext;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.ObjectMapper;
import xyz.byqy.vane.common.error.ApiException;
import xyz.byqy.vane.common.error.CommonErrors;
import xyz.byqy.vane.system.infrastructure.SmsConfigCipher;
import xyz.byqy.vane.system.infrastructure.SmsProviderRepository;
import xyz.byqy.vane.system.infrastructure.SmsProviderRepository.SmsProviderRow;

/** 短信服务商配置（ADR-022）：密钥加密入库、按类型校验、默认项唯一、测试发送。 */
@Service
public class SmsProviderService {

    private static final Pattern CODE_PATTERN = Pattern.compile("^[a-z][a-z0-9-]{1,63}$");
    private static final Pattern SECRET_KEY_PATTERN =
            Pattern.compile(".*(secret|token|password).*", Pattern.CASE_INSENSITIVE);
    private static final Map<String, List<String>> REQUIRED_CONFIG = Map.of(
            "ALIYUN", List.of("accessKeyId", "accessKeySecret", "regionId"),
            "TENCENT", List.of("secretId", "secretKey", "sdkAppId", "region"),
            "GENERIC_HTTP", List.of("url"));

    private final SmsProviderRepository providers;
    private final SmsConfigCipher cipher;
    private final SmsPort smsPort;
    private final SmsPhoneValidator phones;
    private final ObjectMapper json;
    private final Set<String> allowedCountries;

    public SmsProviderService(SmsProviderRepository providers, SmsConfigCipher cipher, SmsPort smsPort,
                              SmsPhoneValidator phones, ObjectMapper json,
                              @Value("${vane.sms.allowed-countries:CN,HK,MO,TW,SG,MY,TH,VN,ID,PH}")
                              String allowedCountries) {
        this.providers = providers;
        this.cipher = cipher;
        this.smsPort = smsPort;
        this.phones = phones;
        this.json = json;
        this.allowedCountries = new LinkedHashSet<>(Arrays.stream(allowedCountries.split(","))
                .map(String::strip).filter(value -> !value.isEmpty()).toList());
    }

    @Transactional(readOnly = true)
    public List<SmsProviderView> list() {
        return providers.findAll().stream().map(this::toView).toList();
    }

    @Transactional(readOnly = true)
    public SmsProviderView get(String code) {
        return toView(require(code));
    }

    @Transactional
    public SmsProviderView create(CreateProvider command) {
        validate(command.code(), command.type(), command.name(), command.config(), command.countries());
        try {
            SmsProviderRow row = new SmsProviderRow(command.code(), command.type(), command.name(),
                    cipher.encrypt(command.code(), json.writeValueAsString(command.config())),
                    cipher.activeVersion(),
                    json.writeValueAsString(command.countries()),
                    json.writeValueAsString(command.optoutKeywords() == null ? List.of() : command.optoutKeywords()),
                    command.enabled() == null || command.enabled(),
                    Boolean.TRUE.equals(command.isDefault()));
            if (row.isDefault()) {
                providers.clearDefaultExcept(row.code());
            }
            providers.insert(row);
            return toView(row);
        } catch (IllegalStateException ex) {
            throw new ApiException(CommonErrors.SMS_NOT_CONFIGURED);
        } catch (DuplicateKeyException ex) {
            throw new ApiException(CommonErrors.SMS_PROVIDER_CODE_TAKEN);
        }
    }

    @Transactional
    public SmsProviderView update(String code, UpdateProvider command) {
        SmsProviderRow existing = require(code);
        Map<String, Object> before = Map.of("name", existing.name(),
                "countries", existing.countriesJson(), "enabled", existing.enabled(),
                "isDefault", existing.isDefault());
        Map<String, String> config = command.config() == null ? null : command.config();
        List<String> countries = command.countries() == null ? readList(existing.countriesJson()) : command.countries();
        List<String> keywords = command.optoutKeywords() == null
                ? readList(existing.optoutKeywordsJson())
                : command.optoutKeywords();
        if (countries.isEmpty() || !allowedCountries.containsAll(countries)) {
            throw new ApiException(CommonErrors.VALIDATION_FAILED);
        }
        String cipherText = existing.configCipher();
        String keyVersion = existing.keyVersion();
        if (config != null) {
            validateConfig(existing.type(), config);
            cipherText = cipher.encrypt(code, json.writeValueAsString(config));
            keyVersion = cipher.activeVersion();
        }
        SmsProviderRow row = new SmsProviderRow(code, existing.type(),
                command.name() == null || command.name().isBlank() ? existing.name() : command.name(),
                cipherText, keyVersion,
                json.writeValueAsString(countries),
                json.writeValueAsString(keywords),
                command.enabled() == null ? existing.enabled() : command.enabled(),
                command.isDefault() == null ? existing.isDefault() : command.isDefault());
        if (row.isDefault()) {
            providers.clearDefaultExcept(code);
        }
        providers.update(row);
        AuditContext.record(before, Map.of("name", row.name(), "countries", row.countriesJson(),
                "enabled", row.enabled(), "isDefault", row.isDefault()),
                "name", "countries", "enabled", "isDefault");
        return toView(row);
    }

    /** 测试发送：不写正式模板绑定，仅验证服务商配置与签名可用。 */
    public SmsSendResult testSend(String code, TestSend command) {
        require(code);
        SmsPhoneValidator.NormalizedPhone phone = phones.normalize(command.phone());
        return smsPort.send(new SmsMessage(code, phone.e164(), phone.countryCode(), "zh-CN",
                command.signature(), command.templateCode(), command.variables() == null ? Map.of() : command.variables()));
    }

    private void validate(String code, String type, String name, Map<String, String> config, List<String> countries) {
        if (code == null || !CODE_PATTERN.matcher(code).matches()) {
            throw new ApiException(CommonErrors.VALIDATION_FAILED);
        }
        if (type == null || !REQUIRED_CONFIG.containsKey(type)) {
            throw new ApiException(CommonErrors.VALIDATION_FAILED);
        }
        if (name == null || name.isBlank() || name.length() > 128) {
            throw new ApiException(CommonErrors.VALIDATION_FAILED);
        }
        validateConfig(type, config);
        if (countries == null || countries.isEmpty() || !allowedCountries.containsAll(countries)) {
            throw new ApiException(CommonErrors.VALIDATION_FAILED);
        }
    }

    private void validateConfig(String type, Map<String, String> config) {
        if (config == null) {
            throw new ApiException(CommonErrors.VALIDATION_FAILED);
        }
        for (String required : REQUIRED_CONFIG.getOrDefault(type, List.of())) {
            String value = config.get(required);
            if (value == null || value.isBlank()) {
                throw new ApiException(CommonErrors.VALIDATION_FAILED);
            }
        }
    }

    private SmsProviderRow require(String code) {
        return providers.findByCode(code)
                .orElseThrow(() -> new ApiException(CommonErrors.SMS_PROVIDER_NOT_FOUND));
    }

    private SmsProviderView toView(SmsProviderRow row) {
        Map<String, String> config = new LinkedHashMap<>();
        try {
            Map<String, String> decrypted = readMap(row.configCipher(), row.keyVersion(), row.code());
            decrypted.forEach((key, value) -> config.put(key, mask(key, value)));
        } catch (IllegalStateException ex) {
            config.put("config", "<密钥不可用，请检查 VANE_SMS_SECRET_KEYS>");
        }
        return new SmsProviderView(row.code(), row.type(), row.name(), config,
                readList(row.countriesJson()), readList(row.optoutKeywordsJson()),
                row.enabled(), row.isDefault(), row.keyVersion());
    }

    private Map<String, String> readMap(String cipherText, String keyVersion, String providerCode) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, String> map = json.readValue(cipher.decrypt(providerCode, keyVersion, cipherText), Map.class);
            return map;
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new IllegalStateException("invalid SMS provider config", ex);
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> readList(String jsonText) {
        if (jsonText == null || jsonText.isBlank()) {
            return List.of();
        }
        return json.readValue(jsonText, List.class);
    }

    private static String mask(String key, String value) {
        if (value == null || !SECRET_KEY_PATTERN.matcher(key).matches()) {
            return value;
        }
        if (value.length() <= 8) {
            return "****";
        }
        return value.substring(0, 4) + "****" + value.substring(value.length() - 4);
    }

    public record CreateProvider(String code, String type, String name, Map<String, String> config,
                                 List<String> countries, List<String> optoutKeywords,
                                 Boolean enabled, Boolean isDefault) {
    }

    public record UpdateProvider(String name, Map<String, String> config, List<String> countries,
                                 List<String> optoutKeywords, Boolean enabled, Boolean isDefault) {
    }

    public record TestSend(String phone, String signature, String templateCode, Map<String, String> variables) {
    }
}
