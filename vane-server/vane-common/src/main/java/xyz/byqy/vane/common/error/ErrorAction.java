package xyz.byqy.vane.common.error;

/**
 * 错误恢复指令：告诉客户端（尤其是 AI Agent）收到错误后下一步该做什么。
 *
 * <p>取值语义稳定，不新增同义值；新增前须评估既有客户端的分支逻辑。</p>
 */
public enum ErrorAction {

    /** 瞬时失败，原样重试即可（可重试）。 */
    RETRY,

    /** 请求内容不合法，改参数后重发。 */
    FIX_PARAMS,

    /** 需要人的决策（如额度、锁定、安全事件），停止并转人工。 */
    ASK_USER,

    /** 访问令牌失效，刷新令牌后重试。 */
    REFRESH_TOKEN,

    /** 与当前状态冲突（并发修改、唯一性以外的状态问题），重新读取状态后再决策。 */
    CONFLICT;

    /** 是否可原样重试。 */
    public boolean retryable() {
        return this == RETRY;
    }
}
