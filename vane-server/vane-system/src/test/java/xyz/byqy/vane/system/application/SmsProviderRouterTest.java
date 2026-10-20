package xyz.byqy.vane.system.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import xyz.byqy.vane.common.error.ApiException;
import xyz.byqy.vane.common.error.CommonErrors;
import xyz.byqy.vane.system.infrastructure.SmsProviderRepository;
import xyz.byqy.vane.system.infrastructure.SmsProviderRepository.SmsProviderRow;

class SmsProviderRouterTest {

    private final SmsProviderRepository providers = mock(SmsProviderRepository.class);
    private final SmsProviderRouter router = new SmsProviderRouter(providers);

    private SmsProviderRow row(String code, String countriesJson, boolean enabled, boolean isDefault) {
        return new SmsProviderRow(code, "ALIYUN", code, "cipher", "1", countriesJson, "[]", enabled, isDefault);
    }

    @Test
    void routesToProviderSupportingCountry() {
        when(providers.findAll()).thenReturn(List.of(
                row("aliyun-cn", "[\"CN\"]", true, false),
                row("tencent-sea", "[\"SG\",\"MY\"]", true, false)));

        assertThat(router.route("SG").code()).isEqualTo("tencent-sea");
    }

    @Test
    void prefersDefaultProviderWhenItSupportsCountry() {
        when(providers.findAll()).thenReturn(List.of(
                row("tencent-sea", "[\"SG\",\"MY\"]", true, true),
                row("aliyun-cn", "[\"CN\"],\"SG\"", true, false)));

        assertThat(router.route("SG").code()).isEqualTo("tencent-sea");
    }

    @Test
    void ignoresDisabledProviders() {
        when(providers.findAll()).thenReturn(List.of(
                row("aliyun-cn", "[\"CN\"]", false, true),
                row("backup", "[\"CN\"]", true, false)));

        assertThat(router.route("CN").code()).isEqualTo("backup");
    }

    @Test
    void rejectsCountryWithoutProvider() {
        when(providers.findAll()).thenReturn(List.of(row("aliyun-cn", "[\"CN\"]", true, true)));

        assertThatThrownBy(() -> router.route("US"))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).errorCode())
                .isEqualTo(CommonErrors.SMS_NUMBER_NOT_SUPPORTED);
    }
}
