package xyz.byqy.vane.system.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;


import tools.jackson.databind.json.JsonMapper;
import xyz.byqy.vane.system.application.SmsMessage;
import xyz.byqy.vane.system.application.SmsSendResult;

class SmsVendorSenderTest {

    private static SmsMessage message() {
        Map<String, String> variables = new LinkedHashMap<>();
        variables.put("code", "123456");
        variables.put("minutes", "5");
        return new SmsMessage("p1", "+8613800138000", "CN", "zh-CN", "vane", "SMS_123", variables);
    }

    @Test
    void aliyunBuildsSendSmsRequest() {
        AliyunSmsSender sender = new AliyunSmsSender(JsonMapper.builder().build());

        com.aliyun.sdk.service.dysmsapi20170525.models.SendSmsRequest request = sender.buildRequest(message());

        assertThat(request.getPhoneNumbers()).isEqualTo("+8613800138000");
        assertThat(request.getSignName()).isEqualTo("vane");
        assertThat(request.getTemplateCode()).isEqualTo("SMS_123");
        assertThat(request.getTemplateParam()).isEqualTo("{\"code\":\"123456\",\"minutes\":\"5\"}");
    }

    @Test
    void aliyunReturnsFailureWhenConfigIncompleteWithoutNetwork() {
        AliyunSmsSender sender = new AliyunSmsSender(JsonMapper.builder().build());

        SmsSendResult result = sender.send(Map.of("regionId", "cn-hangzhou"), message());

        assertThat(result.success()).isFalse();
        assertThat(result.errorCode()).isEqualTo("SMS.VENDOR.ALIYUN_ERROR");
    }

    @Test
    void tencentBuildsSendSmsRequest() {
        TencentSmsSender sender = new TencentSmsSender();

        com.tencentcloudapi.sms.v20210111.models.SendSmsRequest request = sender.buildRequest(Map.of("sdkAppId", "1400000000"), message());

        assertThat(request.getSmsSdkAppId()).isEqualTo("1400000000");
        assertThat(request.getSignName()).isEqualTo("vane");
        assertThat(request.getTemplateId()).isEqualTo("SMS_123");
        assertThat(request.getTemplateParamSet()).containsExactly("123456", "5");
        assertThat(request.getPhoneNumberSet()).containsExactly("+8613800138000");
    }

    @Test
    void tencentReturnsFailureWhenConfigIncompleteWithoutNetwork() {
        TencentSmsSender sender = new TencentSmsSender();

        SmsSendResult result = sender.send(Map.of("sdkAppId", "1400000000"), message());

        assertThat(result.success()).isFalse();
        assertThat(result.errorCode()).isEqualTo("SMS.VENDOR.TENCENT_ERROR");
    }
}
