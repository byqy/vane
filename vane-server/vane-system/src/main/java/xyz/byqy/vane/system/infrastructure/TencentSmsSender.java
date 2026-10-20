package xyz.byqy.vane.system.infrastructure;

import java.util.Map;

import org.springframework.stereotype.Component;

import com.tencentcloudapi.common.Credential;
import com.tencentcloudapi.common.exception.TencentCloudSDKException;
import com.tencentcloudapi.common.profile.ClientProfile;
import com.tencentcloudapi.common.profile.HttpProfile;
import com.tencentcloudapi.sms.v20210111.SmsClient;
import com.tencentcloudapi.sms.v20210111.models.SendSmsRequest;
import com.tencentcloudapi.sms.v20210111.models.SendSmsResponse;
import com.tencentcloudapi.sms.v20210111.models.SendStatus;

import xyz.byqy.vane.system.application.SmsMessage;
import xyz.byqy.vane.system.application.SmsSendResult;
import xyz.byqy.vane.system.application.SmsVendorSender;

/** 腾讯云短信（官方 SDK，同步 API）。 */
@Component
public class TencentSmsSender implements SmsVendorSender {

    private static final String PROVIDER_TYPE = "TENCENT";
    private static final int CONNECT_TIMEOUT_SECONDS = 2;
    private static final int READ_TIMEOUT_SECONDS = 5;

    @Override
    public String providerType() {
        return PROVIDER_TYPE;
    }

    @Override
    public SmsSendResult send(Map<String, String> config, SmsMessage message) {
        try {
            SendSmsResponse response = client(config).SendSms(buildRequest(config, message));
            SendStatus[] statuses = response.getSendStatusSet();
            if (statuses == null || statuses.length == 0) {
                return SmsSendResult.failed("EMPTY_RESPONSE", null);
            }
            SendStatus status = statuses[0];
            if ("Ok".equalsIgnoreCase(status.getCode())) {
                return SmsSendResult.ok(status.getSerialNo(), status.getCode());
            }
            return SmsSendResult.failed(status.getCode(), status.getMessage());
        } catch (TencentCloudSDKException ex) {
            return SmsSendResult.failed(ex.getErrorCode() == null ? "SMS.VENDOR.TENCENT_ERROR" : ex.getErrorCode(),
                    ex.getMessage());
        } catch (RuntimeException ex) {
            return SmsSendResult.failed("SMS.VENDOR.TENCENT_ERROR", ex.getMessage());
        }
    }

    SendSmsRequest buildRequest(Map<String, String> config, SmsMessage message) {
        SendSmsRequest request = new SendSmsRequest();
        request.setSmsSdkAppId(required(config, "sdkAppId"));
        request.setSignName(message.signature());
        request.setTemplateId(message.providerTemplateCode());
        request.setTemplateParamSet(message.variables().values().toArray(String[]::new));
        request.setPhoneNumberSet(new String[]{message.phoneE164()});
        return request;
    }

    private SmsClient client(Map<String, String> config) {
        Credential credential = new Credential(required(config, "secretId"), required(config, "secretKey"));
        HttpProfile httpProfile = new HttpProfile();
        httpProfile.setEndpoint(config.getOrDefault("endpoint", "sms.tencentcloudapi.com"));
        httpProfile.setConnTimeout(CONNECT_TIMEOUT_SECONDS);
        httpProfile.setReadTimeout(READ_TIMEOUT_SECONDS);
        ClientProfile clientProfile = new ClientProfile();
        clientProfile.setHttpProfile(httpProfile);
        return new SmsClient(credential, required(config, "region"), clientProfile);
    }

    private static String required(Map<String, String> config, String key) {
        String value = config.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("missing SMS provider config: " + key);
        }
        return value;
    }
}
