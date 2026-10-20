package xyz.byqy.vane.system.application;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import xyz.byqy.vane.common.error.ApiException;
import xyz.byqy.vane.common.error.CommonErrors;
import xyz.byqy.vane.system.infrastructure.SmsProviderRepository;
import xyz.byqy.vane.system.infrastructure.SmsSignatureRepository;
import xyz.byqy.vane.system.infrastructure.SmsSignatureRepository.SignatureRow;

/** 短信签名维护（ADR-022）：按服务商 + 国家 + 语言，启停与厂商审核状态。 */
@Service
public class SmsSignatureService {

    private final SmsSignatureRepository signatures;
    private final SmsProviderRepository providers;
    private final Set<String> allowedCountries;

    public SmsSignatureService(SmsSignatureRepository signatures, SmsProviderRepository providers,
                               @Value("${vane.sms.allowed-countries:CN,HK,MO,TW,SG,MY,TH,VN,ID,PH}")
                               String allowedCountries) {
        this.signatures = signatures;
        this.providers = providers;
        this.allowedCountries = new LinkedHashSet<>(Arrays.stream(allowedCountries.split(","))
                .map(String::strip).filter(value -> !value.isEmpty()).toList());
    }

    @Transactional(readOnly = true)
    public List<SignatureRow> list(String providerCode) {
        return signatures.findAll(providerCode);
    }

    @Transactional
    public SignatureRow create(CreateSignature command) {
        validate(command.providerCode(), command.signName(), command.countryCode(), command.locale(), null);
        SignatureRow row = new SignatureRow(null, command.providerCode(), command.signName(),
                command.countryCode(), command.locale(), command.status() == null ? "ACTIVE" : command.status(),
                command.approvalStatus());
        signatures.insert(row);
        return signatures.findAll(command.providerCode()).stream()
                .filter(item -> item.signName().equals(row.signName()) && item.countryCode().equals(row.countryCode())
                        && item.locale().equals(row.locale()))
                .findFirst()
                .orElseThrow(() -> new ApiException(CommonErrors.REQUEST_NOT_FOUND));
    }

    @Transactional
    public SignatureRow update(UUID id, UpdateSignature command) {
        SignatureRow existing = signatures.findById(id)
                .orElseThrow(() -> new ApiException(CommonErrors.SMS_SIGNATURE_NOT_FOUND));
        String signName = command.signName() == null ? existing.signName() : command.signName();
        String country = command.countryCode() == null ? existing.countryCode() : command.countryCode();
        String locale = command.locale() == null ? existing.locale() : command.locale();
        String status = command.status() == null ? existing.status() : command.status();
        validate(existing.providerCode(), signName, country, locale, status);
        SignatureRow updated = new SignatureRow(id, existing.providerCode(), signName, country, locale, status,
                command.approvalStatus() == null ? existing.approvalStatus() : command.approvalStatus());
        signatures.update(updated);
        return updated;
    }

    private void validate(String providerCode, String signName, String countryCode, String locale, String status) {
        if (providers.findByCode(providerCode).isEmpty()) {
            throw new ApiException(CommonErrors.SMS_PROVIDER_NOT_FOUND);
        }
        if (signName == null || signName.isBlank() || signName.length() > 64) {
            throw new ApiException(CommonErrors.VALIDATION_FAILED);
        }
        if (countryCode == null || !allowedCountries.contains(countryCode)) {
            throw new ApiException(CommonErrors.VALIDATION_FAILED);
        }
        if (locale == null || locale.isBlank() || locale.length() > 16) {
            throw new ApiException(CommonErrors.VALIDATION_FAILED);
        }
        if (status != null && !Set.of("ACTIVE", "DISABLED").contains(status)) {
            throw new ApiException(CommonErrors.VALIDATION_FAILED);
        }
    }

    public record CreateSignature(String providerCode, String signName, String countryCode, String locale,
                                  String status, String approvalStatus) {
    }

    public record UpdateSignature(String signName, String countryCode, String locale, String status,
                                  String approvalStatus) {
    }
}
