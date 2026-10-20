package xyz.byqy.vane.system.application;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.ObjectMapper;
import xyz.byqy.vane.common.error.ApiException;
import xyz.byqy.vane.common.error.CommonErrors;
import xyz.byqy.vane.common.i18n.MessageResolver;
import xyz.byqy.vane.system.domain.SysMessageTemplate;
import xyz.byqy.vane.system.domain.SysMessageTemplateRepository;
import xyz.byqy.vane.system.infrastructure.SmsCampaignRepository;
import xyz.byqy.vane.system.infrastructure.SmsCampaignRepository.CampaignRow;
import xyz.byqy.vane.system.infrastructure.SmsCampaignRepository.PageResult;
import xyz.byqy.vane.system.infrastructure.SmsCampaignRepository.RecipientInsert;
import xyz.byqy.vane.system.infrastructure.SmsCampaignRepository.RecipientRow;
import xyz.byqy.vane.system.infrastructure.SmsProviderRepository;
import xyz.byqy.vane.system.infrastructure.SmsSignatureRepository;

/** 短信群发活动（ADR-022 P2）：活动状态、CSV 导入预校验、收件人查询。 */
@Service
public class SmsCampaignService {

    private static final int MAX_IMPORT_ROWS = 50_000;
    private static final String SMS_CHANNEL = "SMS";

    private final SmsCampaignRepository campaigns;
    private final SysMessageTemplateRepository templates;
    private final SmsProviderRepository providers;
    private final SmsSignatureRepository signatures;
    private final SmsPhoneValidator phones;
    private final MessageResolver messages;
    private final ObjectMapper json;

    public SmsCampaignService(SmsCampaignRepository campaigns, SysMessageTemplateRepository templates,
                              SmsProviderRepository providers, SmsSignatureRepository signatures,
                              SmsPhoneValidator phones, MessageResolver messages, ObjectMapper json) {
        this.campaigns = campaigns;
        this.templates = templates;
        this.providers = providers;
        this.signatures = signatures;
        this.phones = phones;
        this.messages = messages;
        this.json = json;
    }

    @Transactional(readOnly = true)
    public PageResult<CampaignRow> page(int pageNumber, int pageSize) {
        return campaigns.page(pageNumber, pageSize);
    }

    @Transactional(readOnly = true)
    public CampaignRow get(UUID id) {
        CampaignRow row = campaigns.find(id);
        if (row == null) {
            throw new ApiException(CommonErrors.REQUEST_NOT_FOUND);
        }
        return row;
    }

    @Transactional
    public CampaignRow create(CreateCampaign command) {
        validate(command.name(), command.templateId(), command.providerCode(), command.signatureId(),
                command.locale(), command.budgetAmount());
        UUID id = campaigns.insert(command.name(), command.templateId(), command.providerCode(),
                command.signatureId(), command.scheduledAt(), "DRAFT", command.throttlePerSecond(),
                command.dailyLimit(), command.budgetAmount(), command.locale());
        return get(id);
    }

    @Transactional
    public CampaignRow update(UUID id, UpdateCampaign command) {
        CampaignRow existing = get(id);
        if (!"DRAFT".equals(existing.status())) {
            throw new ApiException(CommonErrors.SMS_CAMPAIGN_STATE_INVALID);
        }
        validate(command.name(), command.templateId(), command.providerCode(), command.signatureId(),
                command.locale(), command.budgetAmount());
        campaigns.updateDraft(id, command.name(), command.templateId(), command.providerCode(),
                command.signatureId(), command.scheduledAt(), command.throttlePerSecond(), command.dailyLimit(),
                command.budgetAmount(), command.locale());
        return get(id);
    }

    @Transactional
    public CampaignRow start(UUID id) {
        CampaignRow row = get(id);
        if (Set.of("DONE", "CANCELLED", "RUNNING").contains(row.status())) {
            throw new ApiException(CommonErrors.SMS_CAMPAIGN_STATE_INVALID);
        }
        campaigns.setScheduledNow(id);
        return get(id);
    }

    @Transactional
    public CampaignRow pause(UUID id) {
        CampaignRow row = get(id);
        if (!Set.of("RUNNING", "SCHEDULED").contains(row.status())) {
            throw new ApiException(CommonErrors.SMS_CAMPAIGN_STATE_INVALID);
        }
        campaigns.updateStatus(id, "PAUSED");
        return get(id);
    }

    @Transactional
    public CampaignRow cancel(UUID id) {
        CampaignRow row = get(id);
        if (Set.of("DONE", "CANCELLED").contains(row.status())) {
            throw new ApiException(CommonErrors.SMS_CAMPAIGN_STATE_INVALID);
        }
        campaigns.updateStatus(id, "CANCELLED");
        return get(id);
    }

    @Transactional(readOnly = true)
    public PageResult<RecipientRow> recipients(UUID id, int pageNumber, int pageSize) {
        get(id);
        return campaigns.recipients(id, pageNumber, pageSize);
    }

    @Transactional(readOnly = true)
    public List<RecipientRow> invalidRecipients(UUID id) {
        get(id);
        return campaigns.invalidRecipients(id);
    }

    /** 预校验：只解析与统计，不写库。 */
    @Transactional(readOnly = true)
    public ImportResult preview(Reader reader) {
        Parsed parsed = parse(reader);
        return parsed.result();
    }

    /** 确认导入：有效行写 PENDING，无效行写 INVALID，已退订写 OPTOUT，重复行跳过。 */
    @Transactional
    public ImportResult importRecipients(UUID campaignId, Reader reader) {
        CampaignRow campaign = get(campaignId);
        if (!"DRAFT".equals(campaign.status())) {
            throw new ApiException(CommonErrors.SMS_CAMPAIGN_STATE_INVALID);
        }
        Parsed parsed = parse(reader);
        List<RecipientInsert> inserts = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (ParsedRow row : parsed.rows()) {
            if (row.phoneE164() == null || !seen.add(row.phoneE164())
                    || campaigns.recipientExists(campaignId, row.phoneE164())) {
                continue;
            }
            String status = row.errorCode() == null ? (row.optedOut() ? "OPTOUT" : "PENDING") : "INVALID";
            inserts.add(new RecipientInsert(row.phoneE164(), row.countryCode(), row.paramsJson(),
                    row.sourceLine(), status, row.errorCode()));
        }
        if (!inserts.isEmpty()) {
            campaigns.insertRecipients(campaignId, inserts);
        }
        campaigns.refreshStats(campaignId);
        return parsed.result();
    }

    private Parsed parse(Reader reader) {
        List<ParsedRow> rows = new ArrayList<>();
        int valid = 0;
        int invalid = 0;
        int duplicates = 0;
        int optedOut = 0;
        List<ImportError> errors = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        try (BufferedReader buffered = new BufferedReader(reader)) {
            String line;
            int lineNumber = 0;
            boolean header = true;
            while ((line = buffered.readLine()) != null) {
                lineNumber++;
                if (line.isBlank()) {
                    continue;
                }
                List<String> cells = splitCsv(line);
                if (header && !cells.isEmpty() && "phone".equalsIgnoreCase(cells.get(0).strip())) {
                    header = false;
                    continue;
                }
                header = false;
                if (rows.size() >= MAX_IMPORT_ROWS) {
                    throw new ApiException(CommonErrors.VALIDATION_FAILED);
                }
                String rawPhone = cells.isEmpty() ? null : cells.get(0).strip();
                String paramsJson = cells.size() > 1 && !cells.get(1).isBlank() ? cells.get(1).strip() : "{}";
                ParsedRow row = validateRow(lineNumber, rawPhone, paramsJson);
                if (row.errorCode() != null) {
                    invalid++;
                    errors.add(new ImportError(lineNumber, rawPhone, row.errorCode()));
                } else if (!seen.add(row.phoneE164())) {
                    duplicates++;
                } else if (row.optedOut()) {
                    optedOut++;
                } else {
                    valid++;
                }
                rows.add(row);
            }
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        return new Parsed(rows, new ImportResult(rows.size(), valid, invalid, duplicates, optedOut, errors));
    }

    private ParsedRow validateRow(int line, String rawPhone, String paramsJson) {
        Map<String, String> params;
        try {
            @SuppressWarnings("unchecked")
            Map<String, String> parsed = json.readValue(paramsJson, Map.class);
            params = parsed;
        } catch (RuntimeException ex) {
            return new ParsedRow(rawPhone, null, "{}", line, CommonErrors.VALIDATION_FAILED.code(), false);
        }
        try {
            SmsPhoneValidator.NormalizedPhone phone = phones.normalize(rawPhone);
            boolean optedOut = campaigns.isOptedOut(sha256(phone.e164()));
            return new ParsedRow(phone.e164(), phone.countryCode(), json.writeValueAsString(params), line,
                    null, optedOut);
        } catch (ApiException ex) {
            return new ParsedRow(rawPhone, null, paramsJson, line, ex.errorCode().code(), false);
        }
    }

    private void validate(String name, UUID templateId, String providerCode, UUID signatureId, String locale,
                          BigDecimal budgetAmount) {
        if (name == null || name.isBlank() || name.length() > 128) {
            throw new ApiException(CommonErrors.VALIDATION_FAILED);
        }
        SysMessageTemplate template = templates.findByIdAndDeletedAtIsNull(templateId)
                .filter(item -> SMS_CHANNEL.equals(item.getChannel()))
                .orElseThrow(() -> new ApiException(CommonErrors.SMS_TEMPLATE_NOT_BOUND));
        if (providers.findByCode(providerCode).isEmpty()) {
            throw new ApiException(CommonErrors.SMS_PROVIDER_NOT_FOUND);
        }
        if (signatureId != null && signatures.findById(signatureId).isEmpty()) {
            throw new ApiException(CommonErrors.SMS_SIGNATURE_NOT_FOUND);
        }
        if (locale == null || !messages.isSupportedLocale(locale)) {
            throw new ApiException(CommonErrors.VALIDATION_FAILED);
        }
        if (budgetAmount != null && budgetAmount.signum() < 0) {
            throw new ApiException(CommonErrors.VALIDATION_FAILED);
        }
    }

    /** 简单 CSV 解析：逗号分隔、双引号包裹、双引号转义。 */
    static List<String> splitCsv(String line) {
        List<String> cells = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        boolean fieldStart = true;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (quoted) {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        current.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else if (fieldStart) {
                    quoted = true;
                    fieldStart = false;
                } else {
                    current.append(c);
                }
            } else if (c == ',' && !quoted) {
                cells.add(current.toString());
                current.setLength(0);
                fieldStart = true;
            } else {
                current.append(c);
                fieldStart = false;
            }
        }
        cells.add(current.toString());
        return cells;
    }

    static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    public record CreateCampaign(String name, UUID templateId, String providerCode, UUID signatureId,
                                 Instant scheduledAt, Integer throttlePerSecond, Integer dailyLimit,
                                 BigDecimal budgetAmount, String locale) {
    }

    public record UpdateCampaign(String name, UUID templateId, String providerCode, UUID signatureId,
                                 Instant scheduledAt, Integer throttlePerSecond, Integer dailyLimit,
                                 BigDecimal budgetAmount, String locale) {
    }

    private record Parsed(List<ParsedRow> rows, ImportResult result) {
    }

    private record ParsedRow(String phoneE164, String countryCode, String paramsJson, int sourceLine,
                             String errorCode, boolean optedOut) {
    }

    public record ImportError(int line, String phone, String errorCode) {
    }

    public record ImportResult(int total, int valid, int invalid, int duplicates, int optedOut,
                               List<ImportError> errors) {
    }
}
