package xyz.byqy.vane.system.infrastructure;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Component;

import com.aliyun.auth.credentials.Credential;
import com.aliyun.auth.credentials.provider.StaticCredentialProvider;
import com.aliyun.sdk.gateway.pop.Configuration;
import com.aliyun.sdk.service.dysmsapi20170525.AsyncClient;
import com.aliyun.sdk.service.dysmsapi20170525.models.SendSmsRequest;
import com.aliyun.sdk.service.dysmsapi20170525.models.SendSmsResponse;
import com.aliyun.sdk.service.dysmsapi20170525.models.SendSmsResponseBody;

import darabonba.core.client.ClientOverrideConfiguration;
import tools.jackson.databind.ObjectMapper;
import xyz.byqy.vane.system.application.SmsMessage;
import xyz.byqy.vane.system.application.SmsSendResult;
import xyz.byqy.vane.system.application.SmsVendorSender;

/** 阿里云短信（官方 SDK 4.x，异步 API 同步等待结果）。 */
@Component
public class AliyunSmsSender implements SmsVendorSender {

    private static final String PROVIDER_TYPE = "ALIYUN";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(5);

    private final ObjectMapper json;

    public AliyunSmsSender(ObjectMapper json) {
        this.json = json;
    }

    @Override
    public String providerType() {
        return PROVIDER_TYPE;
    }

    @Override
    public SmsSendResult send(Map<String, String> config, SmsMessage message) {
        try (AsyncClient client = client(config)) {
            SendSmsResponse response = client.sendSms(buildRequest(message)).get(6, TimeUnit.SECONDS);
            SendSmsResponseBody body = response.getBody();
            if (body != null && "OK".equals(body.getCode())) {
                return SmsSendResult.ok(body.getBizId(), body.getCode());
            }
            String code = body == null ? "EMPTY_RESPONSE" : body.getCode();
            String detail = body == null ? null : body.getMessage();
            return SmsSendResult.failed(code, detail);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return SmsSendResult.failed("SMS.VENDOR.INTERRUPTED", ex.getMessage());
        } catch (Exception ex) {
            return SmsSendResult.failed("SMS.VENDOR.ALIYUN_ERROR", ex.getMessage());
        }
    }

    SendSmsRequest buildRequest(SmsMessage message) {
        return SendSmsRequest.builder()
                .phoneNumbers(message.phoneE164())
                .signName(message.signature())
                .templateCode(message.providerTemplateCode())
                .templateParam(json.writeValueAsString(message.variables()))
                .build();
    }

    private AsyncClient client(Map<String, String> config) {
        Credential credential = Credential.builder()
                .accessKeyId(required(config, "accessKeyId"))
                .accessKeySecret(required(config, "accessKeySecret"))
                .build();
        ClientOverrideConfiguration override = ClientOverrideConfiguration.create()
                .setConnectTimeout(CONNECT_TIMEOUT)
                .setResponseTimeout(READ_TIMEOUT);
        if (config.get("endpoint") != null && !config.get("endpoint").isBlank()) {
            override = override.setEndpointOverride(config.get("endpoint"));
        }
        return AsyncClient.builder()
                .credentialsProvider(StaticCredentialProvider.create(credential))
                .region(required(config, "regionId"))
                .serviceConfiguration(Configuration.create())
                .overrideConfiguration(override)
                .build();
    }

    private static String required(Map<String, String> config, String key) {
        String value = config.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("missing SMS provider config: " + key);
        }
        return value;
    }
}
