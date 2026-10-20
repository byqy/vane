package xyz.byqy.vane.system.infrastructure;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.tencentcloudapi.common.Credential;
import com.tencentcloudapi.common.profile.ClientProfile;
import com.tencentcloudapi.common.profile.HttpProfile;
import com.tencentcloudapi.sms.v20210111.SmsClient;
import com.tencentcloudapi.sms.v20210111.models.PullSmsSendStatus;
import com.tencentcloudapi.sms.v20210111.models.PullSmsSendStatusRequest;

import xyz.byqy.vane.system.application.SmsReceiptPoller;

/** 腾讯云回执轮询：PullSmsSendStatus，按 SerialNo 匹配本地消息 ID。 */
@Component
public class TencentReceiptPoller implements SmsReceiptPoller {

    @Override
    public String providerType() {
        return "TENCENT";
    }

    @Override
    public List<PolledReceipt> poll(Map<String, String> config, List<PollTarget> targets) {
        Map<String, PollTarget> byMessageId = new HashMap<>();
        for (PollTarget target : targets) {
            if (target.providerMessageId() != null) {
                byMessageId.put(target.providerMessageId(), target);
            }
        }
        List<PolledReceipt> receipts = new ArrayList<>();
        try {
            PullSmsSendStatusRequest request = new PullSmsSendStatusRequest();
            request.setSmsSdkAppId(required(config, "sdkAppId"));
            request.setLimit(100L);
            for (PullSmsSendStatus status : client(config).PullSmsSendStatus(request).getPullSmsSendStatusSet()) {
                PollTarget target = byMessageId.get(status.getSerialNo());
                if (target == null) {
                    continue;
                }
                receipts.add(new PolledReceipt(target.logId(), mapStatus(status.getReportStatus()),
                        status.getReportStatus(),
                        status.getUserReceiveTime() == null || status.getUserReceiveTime() <= 0 ? null
                                : Instant.ofEpochSecond(status.getUserReceiveTime()),
                        "{\"status\":\"" + status.getReportStatus() + "\"}"));
            }
        } catch (Exception ex) {
            // 拉取失败时本轮跳过，等待下个周期
        }
        return receipts;
    }

    static String mapStatus(String reportStatus) {
        if (reportStatus == null) {
            return "UNKNOWN";
        }
        return switch (reportStatus.strip().toUpperCase()) {
            case "DELIVRD", "SUCCESS", "OK" -> "DELIVERED";
            case "UNDELIV", "FAIL", "REJECTD", "EXPIRED" -> "FAILED";
            default -> "UNKNOWN";
        };
    }

    private SmsClient client(Map<String, String> config) {
        Credential credential = new Credential(required(config, "secretId"), required(config, "secretKey"));
        HttpProfile httpProfile = new HttpProfile();
        httpProfile.setEndpoint(config.getOrDefault("endpoint", "sms.tencentcloudapi.com"));
        httpProfile.setConnTimeout(2);
        httpProfile.setReadTimeout(5);
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
