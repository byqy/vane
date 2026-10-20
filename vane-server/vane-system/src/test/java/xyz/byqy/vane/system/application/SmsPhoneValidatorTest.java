package xyz.byqy.vane.system.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import xyz.byqy.vane.common.error.ApiException;
import xyz.byqy.vane.common.error.CommonErrors;

class SmsPhoneValidatorTest {

    private static final String ALLOWED = "CN,HK,MO,TW,SG,MY,TH,VN,ID,PH";

    private final SmsPhoneValidator validator = new SmsPhoneValidator(ALLOWED);

    @Test
    void normalizesInternationalChineseNumber() {
        SmsPhoneValidator.NormalizedPhone phone = validator.normalize("+86 138-0013-8000");
        assertThat(phone.e164()).isEqualTo("+8613800138000");
        assertThat(phone.countryCode()).isEqualTo("CN");
    }

    @Test
    void acceptsDomesticNumberWithoutCountryCode() {
        SmsPhoneValidator.NormalizedPhone phone = validator.normalize("13800138000");
        assertThat(phone.e164()).isEqualTo("+8613800138000");
        assertThat(phone.countryCode()).isEqualTo("CN");
    }

    @Test
    void acceptsAllowedRegions() {
        assertThat(validator.normalize("+85221234567").countryCode()).isEqualTo("HK");
        assertThat(validator.normalize("+6591234567").countryCode()).isEqualTo("SG");
    }

    @Test
    void rejectsInvalidNumber() {
        assertThatThrownBy(() -> validator.normalize("not-a-phone"))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).errorCode())
                .isEqualTo(CommonErrors.SMS_NUMBER_INVALID);
        assertThatThrownBy(() -> validator.normalize(" "))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).errorCode())
                .isEqualTo(CommonErrors.SMS_NUMBER_INVALID);
    }

    @Test
    void rejectsCountryOutsideWhitelist() {
        assertThatThrownBy(() -> validator.normalize("+14155552671"))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).errorCode())
                .isEqualTo(CommonErrors.SMS_NUMBER_NOT_SUPPORTED);
    }
}
