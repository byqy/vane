package xyz.byqy.vane.system.application;

import java.util.List;

import org.springframework.stereotype.Service;

import xyz.byqy.vane.common.error.ApiException;
import xyz.byqy.vane.common.error.CommonErrors;
import xyz.byqy.vane.system.infrastructure.SmsProviderRepository;
import xyz.byqy.vane.system.infrastructure.SmsProviderRepository.SmsProviderRow;

/** 服务商路由（ADR-022 P3）：按号码国家选择支持该国家的启用服务商，默认项优先。 */
@Service
public class SmsProviderRouter {

    private final SmsProviderRepository providers;

    public SmsProviderRouter(SmsProviderRepository providers) {
        this.providers = providers;
    }

    public SmsProviderRow route(String countryCode) {
        List<SmsProviderRow> enabled = providers.findAll().stream()
                .filter(SmsProviderRow::enabled)
                .filter(row -> row.countriesJson() != null && row.countriesJson().contains("\"" + countryCode + "\""))
                .toList();
        if (enabled.isEmpty()) {
            throw new ApiException(CommonErrors.SMS_NUMBER_NOT_SUPPORTED);
        }
        return enabled.stream().filter(SmsProviderRow::isDefault).findFirst().orElse(enabled.get(0));
    }
}
