package xyz.byqy.vane.system.application;

import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import xyz.byqy.vane.common.error.ApiException;
import xyz.byqy.vane.common.error.CommonErrors;
import xyz.byqy.vane.system.infrastructure.SmsOptoutRepository;

/** 退订名单维护（ADR-022）：按手机号哈希存储，支持手工新增与移除。 */
@Service
public class SmsOptoutService {

    private final SmsOptoutRepository optouts;
    private final SmsPhoneValidator phones;

    public SmsOptoutService(SmsOptoutRepository optouts, SmsPhoneValidator phones) {
        this.optouts = optouts;
        this.phones = phones;
    }

    @Transactional(readOnly = true)
    public SmsOptoutRepository.PageResult<SmsOptoutRepository.OptoutRow> page(int pageNumber, int pageSize) {
        return optouts.page(pageNumber, pageSize);
    }

    @Transactional
    public boolean create(String phone, String scope, String reason) {
        String normalizedScope = scope == null || scope.isBlank() ? "MARKETING" : scope.strip().toUpperCase();
        if (!Set.of("ALL", "MARKETING").contains(normalizedScope)) {
            throw new ApiException(CommonErrors.VALIDATION_FAILED);
        }
        String e164 = phones.normalize(phone).e164();
        return optouts.insertIfAbsent(SmsInboundService.sha256(e164), normalizedScope, "MANUAL", reason);
    }

    @Transactional
    public void delete(UUID id) {
        if (!optouts.delete(id)) {
            throw new ApiException(CommonErrors.REQUEST_NOT_FOUND);
        }
    }
}
