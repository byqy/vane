package xyz.byqy.vane.api.admin;

import java.time.Instant;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import xyz.byqy.vane.system.application.SmsCallbackAuth;
import xyz.byqy.vane.system.application.SmsInboundService;
import xyz.byqy.vane.system.application.SmsReceiptService;

/** 短信回执/上行回调（ADR-022 P3）：按 provider 的 callbackToken 鉴权。 */
@RestController
@RequestMapping("/api/v1/public/sms")
public class SmsCallbackController {

    private static final String TOKEN_HEADER = "X-SMS-Callback-Token";

    private final SmsReceiptService receipts;
    private final SmsInboundService inbounds;

    public SmsCallbackController(SmsReceiptService receipts, SmsInboundService inbounds) {
        this.receipts = receipts;
        this.inbounds = inbounds;
    }

    @PostMapping("/receipt/{provider}")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void receipt(@PathVariable String provider,
                        @RequestHeader(value = TOKEN_HEADER, required = false) String token,
                        @RequestBody CallbackRequest body) {
        receipts.record(provider, token, body.toPayload());
    }

    @PostMapping("/inbound/{provider}")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void inbound(@PathVariable String provider,
                        @RequestHeader(value = TOKEN_HEADER, required = false) String token,
                        @RequestBody CallbackRequest body) {
        inbounds.record(provider, token, body.toPayload());
    }

    public record CallbackRequest(String providerMessageId, String phone, String status, String providerStatus,
                                  Instant occurredAt, String content, Map<String, Object> raw) {

        SmsCallbackAuth.CallbackPayload toPayload() {
            return new SmsCallbackAuth.CallbackPayload(providerMessageId, phone, status, providerStatus,
                    occurredAt, content, raw);
        }
    }
}
