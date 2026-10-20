package xyz.byqy.vane.system.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.StringReader;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import tools.jackson.databind.json.JsonMapper;
import xyz.byqy.vane.common.error.ApiException;
import xyz.byqy.vane.common.error.CommonErrors;
import xyz.byqy.vane.common.i18n.MessageResolver;
import xyz.byqy.vane.system.domain.SysMessageTemplate;
import xyz.byqy.vane.system.domain.SysMessageTemplateRepository;
import xyz.byqy.vane.system.infrastructure.SmsCampaignRepository;
import xyz.byqy.vane.system.infrastructure.SmsCampaignRepository.CampaignRow;
import xyz.byqy.vane.system.infrastructure.SmsCampaignRepository.RecipientInsert;
import xyz.byqy.vane.system.infrastructure.SmsProviderRepository;
import xyz.byqy.vane.system.infrastructure.SmsProviderRepository.SmsProviderRow;
import xyz.byqy.vane.system.infrastructure.SmsSignatureRepository;

class SmsCampaignServiceTest {

    private final SmsCampaignRepository campaigns = mock(SmsCampaignRepository.class);
    private final SysMessageTemplateRepository templates = mock(SysMessageTemplateRepository.class);
    private final SmsProviderRepository providers = mock(SmsProviderRepository.class);
    private final SmsSignatureRepository signatures = mock(SmsSignatureRepository.class);
    private final SmsPhoneValidator phones = mock(SmsPhoneValidator.class);
    private final MessageResolver messages = mock(MessageResolver.class);

    private final SmsCampaignService service = new SmsCampaignService(campaigns, templates, providers, signatures,
            phones, messages, JsonMapper.builder().build());

    private final UUID templateId = UUID.randomUUID();
    private final UUID campaignId = UUID.randomUUID();

    private void givenValidCampaign() {
        SysMessageTemplate template = mock(SysMessageTemplate.class);
        when(template.getChannel()).thenReturn("SMS");
        when(templates.findByIdAndDeletedAtIsNull(templateId)).thenReturn(Optional.of(template));
        when(providers.findByCode("aliyun-cn")).thenReturn(Optional.of(new SmsProviderRow("aliyun-cn", "ALIYUN",
                "阿里云", "cipher", "1", "[\"CN\"]", "[]", true, true)));
        when(messages.isSupportedLocale("zh-CN")).thenReturn(true);
    }

    private CampaignRow campaign(String status) {
        return new CampaignRow(campaignId, "活动", templateId, "password.reset", "aliyun-cn", null, status,
                null, 100, null, null, "zh-CN", 0, 0, 0, Instant.now());
    }

    @Test
    void createsDraftCampaign() {
        givenValidCampaign();
        when(campaigns.insert(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(campaignId);
        when(campaigns.find(campaignId)).thenReturn(campaign("DRAFT"));

        CampaignRow created = service.create(new SmsCampaignService.CreateCampaign("活动", templateId,
                "aliyun-cn", null, null, 100, 1000, null, "zh-CN"));

        assertThat(created.id()).isEqualTo(campaignId);
        assertThat(created.status()).isEqualTo("DRAFT");
    }

    @Test
    void rejectsStartWhenCampaignAlreadyDone() {
        when(campaigns.find(campaignId)).thenReturn(campaign("DONE"));

        assertThatThrownBy(() -> service.start(campaignId))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).errorCode())
                .isEqualTo(CommonErrors.SMS_CAMPAIGN_STATE_INVALID);
        verify(campaigns, never()).setScheduledNow(any());
    }

    @Test
    void startsDraftCampaignBySchedulingNow() {
        when(campaigns.find(campaignId)).thenReturn(campaign("DRAFT"), campaign("SCHEDULED"));

        CampaignRow started = service.start(campaignId);

        verify(campaigns).setScheduledNow(campaignId);
        assertThat(started.status()).isEqualTo("SCHEDULED");
    }

    @Test
    void cancelsRunningCampaign() {
        when(campaigns.find(campaignId)).thenReturn(campaign("RUNNING"), campaign("CANCELLED"));

        CampaignRow cancelled = service.cancel(campaignId);

        verify(campaigns).updateStatus(campaignId, "CANCELLED");
        assertThat(cancelled.status()).isEqualTo("CANCELLED");
    }

    @Test
    void previewCountsValidInvalidDuplicateAndOptoutRows() {
        when(phones.normalize("+8613800138000"))
                .thenReturn(new SmsPhoneValidator.NormalizedPhone("+8613800138000", "CN"));
        when(phones.normalize("+8613900139000"))
                .thenReturn(new SmsPhoneValidator.NormalizedPhone("+8613900139000", "CN"));
        when(phones.normalize("bad"))
                .thenThrow(new ApiException(CommonErrors.SMS_NUMBER_INVALID));
        when(phones.normalize("+14155552671"))
                .thenThrow(new ApiException(CommonErrors.SMS_NUMBER_NOT_SUPPORTED));
        when(campaigns.isOptedOut(SmsCampaignService.sha256("+8613900139000"))).thenReturn(true);

        String csv = """
                phone,params
                +8613800138000,{"code":"1"}
                +8613800138000,{"code":"1"}
                bad,{}
                +14155552671,{}
                +8613900139000,{"code":"2"}
                """;

        SmsCampaignService.ImportResult result = service.preview(new StringReader(csv));

        assertThat(result.total()).isEqualTo(5);
        assertThat(result.valid()).withFailMessage("result=%s", result).isEqualTo(1);
        assertThat(result.invalid()).isEqualTo(2);
        assertThat(result.duplicates()).isEqualTo(1);
        assertThat(result.optedOut()).isEqualTo(1);
        assertThat(result.errors()).hasSize(2);
    }

    @SuppressWarnings("unchecked")
    @Test
    void importWritesValidInvalidAndOptoutRows() {
        when(campaigns.find(campaignId)).thenReturn(campaign("DRAFT"));
        when(phones.normalize("+8613800138000"))
                .thenReturn(new SmsPhoneValidator.NormalizedPhone("+8613800138000", "CN"));
        when(phones.normalize("bad"))
                .thenThrow(new ApiException(CommonErrors.SMS_NUMBER_INVALID));
        when(campaigns.isOptedOut(SmsCampaignService.sha256("+8613800138000"))).thenReturn(false);

        String csv = """
                +8613800138000,{"code":"1"}
                bad,{}
                """;

        SmsCampaignService.ImportResult result = service.importRecipients(campaignId, new StringReader(csv));

        assertThat(result.valid()).isEqualTo(1);
        assertThat(result.invalid()).isEqualTo(1);
        ArgumentCaptor<List<RecipientInsert>> captor = ArgumentCaptor.forClass(List.class);
        verify(campaigns).insertRecipients(eq(campaignId), captor.capture());
        assertThat(captor.getValue()).hasSize(2);
        assertThat(captor.getValue().get(0).status()).isEqualTo("PENDING");
        assertThat(captor.getValue().get(0).paramsJson()).contains("\"code\"");
        assertThat(captor.getValue().get(1).status()).isEqualTo("INVALID");
        verify(campaigns).refreshStats(campaignId);
    }

    @Test
    void rejectsImportWhenCampaignNotDraft() {
        when(campaigns.find(campaignId)).thenReturn(campaign("RUNNING"));

        assertThatThrownBy(() -> service.importRecipients(campaignId, new StringReader("+8613800138000,{}")))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).errorCode())
                .isEqualTo(CommonErrors.SMS_CAMPAIGN_STATE_INVALID);
        verify(campaigns, never()).insertRecipients(any(), any());
    }

    @Test
    void splitCsvHandlesQuotedCommasAndEscapedQuotes() {
        assertThat(SmsCampaignService.splitCsv("+8613800138000,\"{\"\"a\"\":\"\"b,c\"\"}\"")).hasSize(2);
        assertThat(SmsCampaignService.splitCsv("a,b,c")).containsExactly("a", "b", "c");
    }

    @Test
    void rejectsCampaignWhenTemplateIsNotSmsChannel() {
        SysMessageTemplate template = mock(SysMessageTemplate.class);
        when(template.getChannel()).thenReturn("EMAIL");
        when(templates.findByIdAndDeletedAtIsNull(templateId)).thenReturn(Optional.of(template));

        assertThatThrownBy(() -> service.create(new SmsCampaignService.CreateCampaign("活动", templateId,
                "aliyun-cn", null, null, 100, 1000, null, "zh-CN")))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).errorCode())
                .isEqualTo(CommonErrors.SMS_TEMPLATE_NOT_BOUND);
        verify(campaigns, never()).insert(any(), any(), any(), any(), any(), any(), any(), any(), any(),
                anyString());
    }
}
