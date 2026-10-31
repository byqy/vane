package xyz.byqy.vane.bootstrap.idempotency;

import java.io.IOException;
import java.util.Set;

import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 演练模式（ADR-030 P2）：请求头 {@code X-Vane-Dry-Run: true} 时，整个请求在一个
 * 恒回滚的事务里执行——校验、权限、审计逻辑照常跑，**数据库写入最终回滚**。
 *
 * <p>限制：外部副作用（短信 / 邮件 / 文件落盘）不会被回滚，脚本类操作请勿在演练模式下调用。
 * 演练请求不参与幂等（见 {@link IdempotencySupport#isEligible}），避免把「假成功」写进幂等记录。</p>
 */
public class DryRunFilter extends OncePerRequestFilter {

    /** 演练开关请求头。 */
    public static final String HEADER = "X-Vane-Dry-Run";

    /** 会改数据的 HTTP 方法。 */
    private static final Set<String> WRITE_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    private final TransactionTemplate transaction;

    public DryRunFilter(PlatformTransactionManager transactionManager) {
        this.transaction = new TransactionTemplate(transactionManager);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!isDryRun(request) || !WRITE_METHODS.contains(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }
        // 告诉调用方：这次请求没有落库
        response.setHeader(HEADER, "true");
        transaction.executeWithoutResult(status -> {
            try {
                chain.doFilter(request, response);
            } catch (IOException | ServletException e) {
                throw new DryRunException(e);
            } finally {
                status.setRollbackOnly();
            }
        });
    }

    private boolean isDryRun(HttpServletRequest request) {
        return "true".equalsIgnoreCase(request.getHeader(HEADER));
    }

    /** 把过滤器链的受检异常包成运行时异常，交给外层还原。 */
    static final class DryRunException extends RuntimeException {
        DryRunException(Throwable cause) {
            super(cause);
        }
    }
}
