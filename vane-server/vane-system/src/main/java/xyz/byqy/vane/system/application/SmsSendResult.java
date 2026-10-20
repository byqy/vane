package xyz.byqy.vane.system.application;

/** 短信发送结果：成功时带厂商消息 ID，失败时带厂商错误码（已映射前的原始值）。 */
public record SmsSendResult(boolean success, String providerMessageId, String providerStatus, String errorCode) {

    public static SmsSendResult ok(String providerMessageId, String providerStatus) {
        return new SmsSendResult(true, providerMessageId, providerStatus, null);
    }

    public static SmsSendResult failed(String errorCode, String providerStatus) {
        return new SmsSendResult(false, null, providerStatus, errorCode);
    }
}
