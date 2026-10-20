package xyz.byqy.vane.system.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.ObjectMapper;
import xyz.byqy.vane.common.error.ApiException;
import xyz.byqy.vane.common.error.CommonErrors;
import xyz.byqy.vane.system.infrastructure.SmsInboundRepository;
import xyz.byqy.vane.system.infrastructure.SmsOptoutRepository;
import xyz.byqy.vane.system.infrastructure.SmsProviderRepository;
import xyz.byqy.vane.system.infrastructure.SmsConfigCipher;

/** 短信上行（ADR-022 P3）：落库 + 退订关键词匹配 + 自动写入退订名单。 */
@Service
public class SmsInboundService {

    private static final String MASK_SUFFIX = "****";

    private final SmsInboundRepository inbounds;
    private final SmsOptoutRepository optouts;
    private final SmsProviderRepository providers;
    private final SmsConfigCipher cipher;
    private final SmsCallbackAuth auth;
    private final SmsPhoneValidator phones;
    private final ObjectMapper json;

    public SmsInboundService(SmsInboundRepository inbounds, SmsOptoutRepository optouts,
                             SmsProviderRepository providers, SmsConfigCipher cipher, SmsCallbackAuth auth,
                             SmsPhoneValidator phones, ObjectMapper json) {
        this.inbounds = inbounds;
        this.optouts = optouts;
        this.providers = providers;
        this.cipher = cipher;
        this.auth = auth;
        this.phones = phones;
        this.json = json;
    }

    @Transactional
    public void record(String providerCode, String token, SmsCallbackAuth.CallbackPayload payload) {
        if (!auth.verify(providerCode, token)) {
            throw new ApiException(CommonErrors.AUTH_FORBIDDEN);
        }
        if (payload.phone() == null || payload.phone().isBlank() || payload.content() == null
                || payload.content().isBlank()) {
            throw new ApiException(CommonErrors.VALIDATION_FAILED);
        }
        String e164;
        try {
            e164 = phones.normalize(payload.phone()).e164();
        } catch (ApiException ex) {
            e164 = payload.phone().strip();
        }
        String phoneHash = sha256(e164);
        String keyword = matchKeyword(providerCode, payload.content());
        boolean processed = false;
        if (keyword != null) {
            optouts.insertIfAbsent(phoneHash, "MARKETING", "INBOUND", payload.content());
            processed = true;
        }
        inbounds.append(providerCode, payload.providerMessageId(), mask(e164), phoneHash, payload.content(),
                payload.occurredAt() == null ? Instant.now() : payload.occurredAt(), processed, keyword);
    }

    @Transactional(readOnly = true)
    public SmsInboundRepository.PageResult<SmsInboundRepository.InboundRow> page(int pageNumber, int pageSize) {
        return inbounds.page(pageNumber, pageSize);
    }

    private String matchKeyword(String providerCode, String content) {
        List<String> keywords = keywordConfig(providerCode);
        String normalized = content.strip();
        for (String keyword : keywords) {
            if (keyword != null && !keyword.isBlank() && normalized.equalsIgnoreCase(keyword.strip())) {
                return keyword;
            }
        }
        return null;
    }

    private List<String> keywordConfig(String providerCode) {
        return providers.findByCode(providerCode)
                .map(row -> {
                    try {
                        @SuppressWarnings("unchecked")
                        Map<String, String> config = json.readValue(
                                cipher.decrypt(row.code(), row.keyVersion(), row.configCipher()), Map.class);
                        String raw = config.get("optoutKeywords");
                        if (raw != null && !raw.isBlank()) {
                            @SuppressWarnings("unchecked")
                            List<String> parsed = json.readValue(raw, List.class);
                            if (!parsed.isEmpty()) {
                                return parsed;
                            }
                        }
                    } catch (RuntimeException ignored) {
                        // 配置不可解密时回退默认关键词
                    }
                    return SmsCallbackAuth.defaultOptoutKeywords();
                })
                .orElse(SmsCallbackAuth.defaultOptoutKeywords());
    }

    private static String mask(String phone) {
        if (phone == null || phone.length() <= 7) {
            return MASK_SUFFIX;
        }
        int prefix = Math.min(5, phone.length() - 4);
        return phone.substring(0, prefix) + MASK_SUFFIX + phone.substring(phone.length() - 4);
    }

    static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
