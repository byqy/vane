package xyz.byqy.vane.system.infrastructure;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Component;

import com.aliyun.auth.credentials.Credential;
import com.aliyun.auth.credentials.provider.StaticCredentialProvider;
import com.aliyun.sdk.gateway.pop.Configuration;
import com.aliyun.sdk.service.dysmsapi20170525.AsyncClient;
import com.aliyun.sdk.service.dysmsapi20170525.models.QuerySendDetailsRequest;
import com.aliyun.sdk.service.dysmsapi20170525.models.QuerySendDetailsResponse;
import com.aliyun.sdk.service.dysmsapi20170525.models.QuerySendDetailsResponseBody;

import darabonba.core.client.ClientOverrideConfiguration;
import xyz.byqy.vane.system.application.SmsReceiptPoller;

/** 阿里云回执轮询：QuerySendDetails（按手机号 + 发送日期 + BizId 查询）。 */
@Component
public class AliyunReceiptPoller implements SmsReceiptPoller {

    private static final ZoneId ALIYUN_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public String providerType() {
        return "ALIYUN";
    }

    @Override
    public List<PolledReceipt> poll(Map<String, String> config, List<PollTarget> targets) {
        List<PolledReceipt> receipts = new ArrayList<>();
        try (AsyncClient client = client(config)) {
            for (PollTarget target : targets) {
                try {
                    QuerySendDetailsRequest request = QuerySendDetailsRequest.builder()
                            .phoneNumber(target.phoneE164())
                            .sendDate(sendDate(target.sentAt()))
                            .pageSize(50L)
                            .currentPage(1L)
                            .bizId(target.providerMessageId())
                            .build();
                    QuerySendDetailsResponse response = client.querySendDetails(request)
                            .get(6, TimeUnit.SECONDS);
                    QuerySendDetailsResponseBody body = response.getBody();
                    if (body == null || body.getSmsSendDetailDTOs() == null
                            || body.getSmsSendDetailDTOs().getSmsSendDetailDTO() == null) {
                        continue;
                    }
                    body.getSmsSendDetailDTOs().getSmsSendDetailDTO().stream()
                            .filter(detail -> target.providerMessageId() == null
                                    || target.providerMessageId().equals(detail.getOutId())
                                    || target.phoneE164().equals(detail.getPhoneNum()))
                            .findFirst()
                            .ifPresent(detail -> receipts.add(new PolledReceipt(target.logId(),
                                    mapStatus(detail.getSendStatus()),
                                    detail.getErrCode() == null ? String.valueOf(detail.getSendStatus())
                                            : detail.getErrCode(),
                                    parseTime(detail.getReceiveDate()),
                                    "{\"status\":" + detail.getSendStatus() + "}")));
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    return receipts;
                } catch (Exception ex) {
                    // 单条失败不影响其它日志
                }
            }
        }
        return receipts;
    }

    static String mapStatus(Long sendStatus) {
        if (sendStatus == null) {
            return "UNKNOWN";
        }
        return switch (sendStatus.intValue()) {
            case 1 -> "PENDING";
            case 2 -> "FAILED";
            case 3 -> "DELIVERED";
            default -> "UNKNOWN";
        };
    }

    private static String sendDate(Instant instant) {
        return DATE.format((instant == null ? Instant.now() : instant).atZone(ALIYUN_ZONE));
    }

    private static Instant parseTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return java.time.LocalDateTime.parse(value, TIME).atZone(ALIYUN_ZONE).toInstant();
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private AsyncClient client(Map<String, String> config) {
        Credential credential = Credential.builder()
                .accessKeyId(required(config, "accessKeyId"))
                .accessKeySecret(required(config, "accessKeySecret"))
                .build();
        ClientOverrideConfiguration override = ClientOverrideConfiguration.create()
                .setConnectTimeout(Duration.ofSeconds(2))
                .setResponseTimeout(Duration.ofSeconds(5));
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
