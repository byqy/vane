package xyz.byqy.vane.system.application;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;

import xyz.byqy.vane.common.error.ApiException;
import xyz.byqy.vane.common.error.CommonErrors;

/** 手机号规范化与一期国家/地区白名单（ADR-022）。 */
@Component
public class SmsPhoneValidator {

    private static final String DEFAULT_REGION = "CN";

    private final PhoneNumberUtil phoneNumbers = PhoneNumberUtil.getInstance();
    private final Set<String> allowedCountries;

    public SmsPhoneValidator(
            @Value("${vane.sms.allowed-countries:CN,HK,MO,TW,SG,MY,TH,VN,ID,PH}") String allowedCountries) {
        this.allowedCountries = new LinkedHashSet<>(Arrays.stream(allowedCountries.split(","))
                .map(String::strip)
                .filter(value -> !value.isEmpty())
                .toList());
    }

    /** 解析为 E.164 并校验国家白名单；国内号码可省略国家码（按 CN 解析）。 */
    public NormalizedPhone normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ApiException(CommonErrors.SMS_NUMBER_INVALID);
        }
        Phonenumber.PhoneNumber number = parse(raw.strip());
        if (number == null || !phoneNumbers.isValidNumber(number)) {
            throw new ApiException(CommonErrors.SMS_NUMBER_INVALID);
        }
        String region = phoneNumbers.getRegionCodeForNumber(number);
        if (region == null) {
            throw new ApiException(CommonErrors.SMS_NUMBER_INVALID);
        }
        if (!allowedCountries.isEmpty() && !allowedCountries.contains(region)) {
            throw new ApiException(CommonErrors.SMS_NUMBER_NOT_SUPPORTED);
        }
        return new NormalizedPhone(phoneNumbers.format(number, PhoneNumberUtil.PhoneNumberFormat.E164), region);
    }

    private Phonenumber.PhoneNumber parse(String raw) {
        try {
            return phoneNumbers.parse(raw, "ZZ");
        } catch (NumberParseException ignored) {
            try {
                return phoneNumbers.parse(raw, DEFAULT_REGION);
            } catch (NumberParseException ex) {
                return null;
            }
        }
    }

    /** 规范化后的号码与国际区号（ISO-3166 alpha-2）。 */
    public record NormalizedPhone(String e164, String countryCode) {
    }
}
