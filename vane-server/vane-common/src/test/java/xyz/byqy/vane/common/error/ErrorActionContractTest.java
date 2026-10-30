package xyz.byqy.vane.common.error;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * 错误恢复指令的契约约束（ADR-030 语义层）。
 *
 * <p>Agent 依赖 {@code action} 决定下一步动作，因此每个已注册错误码都必须声明，
 * 且取值集合固定。</p>
 */
class ErrorActionContractTest {

    @Test
    void everyRegisteredCodeDeclaresAction() {
        assertThat(ErrorCodeRegistry.all()).isNotEmpty();
        assertThat(ErrorCodeRegistry.all())
                .allSatisfy(code -> assertThat(code.action()).as(code.code()).isNotNull());
    }

    @Test
    void retryableMatchesActionSemantics() {
        assertThat(ErrorCodeRegistry.all())
                .allSatisfy(code -> assertThat(code.retryable())
                        .as(code.code())
                        .isEqualTo(code.action().retryable()));
    }

    @Test
    void onlyRetryActionIsRetryable() {
        assertThat(ErrorCodeRegistry.all())
                .filteredOn(ErrorCode::retryable)
                .allSatisfy(code -> assertThat(code.action()).as(code.code()).isEqualTo(ErrorAction.RETRY));
    }

    @Test
    void actionValuesAreStable() {
        assertThat(ErrorAction.values()).containsExactlyInAnyOrder(
                ErrorAction.RETRY,
                ErrorAction.FIX_PARAMS,
                ErrorAction.ASK_USER,
                ErrorAction.REFRESH_TOKEN,
                ErrorAction.CONFLICT);
    }
}
