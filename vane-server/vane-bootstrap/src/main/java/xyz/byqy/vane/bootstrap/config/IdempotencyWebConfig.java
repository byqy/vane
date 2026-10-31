package xyz.byqy.vane.bootstrap.config;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import xyz.byqy.vane.bootstrap.idempotency.ConfirmationInterceptor;
import xyz.byqy.vane.bootstrap.idempotency.DryRunFilter;
import xyz.byqy.vane.bootstrap.idempotency.IdempotencyFilter;
import xyz.byqy.vane.bootstrap.idempotency.IdempotencyInterceptor;
import xyz.byqy.vane.iam.application.ServiceAccountDetector;
import xyz.byqy.vane.system.application.ConfirmationStore;
import xyz.byqy.vane.system.application.IdempotencyStore;

/**
 * 幂等执行的装配（ADR-030 执行层）。
 *
 * <p>过滤器排在安全链之后，保证操作者身份可读；拦截器只处理带幂等键的创建类请求。</p>
 */
@Configuration
public class IdempotencyWebConfig implements WebMvcConfigurer {

    private final IdempotencyStore store;
    private final ConfirmationStore confirmations;
    private final ServiceAccountDetector serviceAccounts;
    private final Duration ttl;
    private final int maxBodyBytes;

    public IdempotencyWebConfig(IdempotencyStore store, ConfirmationStore confirmations,
                                ServiceAccountDetector serviceAccounts,
                                @Value("${vane.idempotency.ttl:PT24H}") Duration ttl,
                                @Value("${vane.idempotency.max-body-bytes:1048576}") int maxBodyBytes) {
        this.store = store;
        this.confirmations = confirmations;
        this.serviceAccounts = serviceAccounts;
        this.ttl = ttl;
        this.maxBodyBytes = maxBodyBytes;
    }

    @Bean
    public FilterRegistrationBean<IdempotencyFilter> idempotencyFilter() {
        FilterRegistrationBean<IdempotencyFilter> registration =
                new FilterRegistrationBean<>(new IdempotencyFilter(store, ttl, maxBodyBytes));
        // Spring Security 的过滤链默认排在 -100，本过滤器排在其后以读取到操作者身份
        registration.setOrder(-90);
        registration.addUrlPatterns("/api/v1/*");
        return registration;
    }

    @Bean
    public FilterRegistrationBean<DryRunFilter> dryRunFilter(PlatformTransactionManager transactionManager) {
        FilterRegistrationBean<DryRunFilter> registration = new FilterRegistrationBean<>(new DryRunFilter(transactionManager));
        // 排在安全链与幂等过滤器之后：先认身份，再决定是否演练
        registration.setOrder(-80);
        registration.addUrlPatterns("/api/v1/*");
        return registration;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new IdempotencyInterceptor(store, ttl)).addPathPatterns("/api/v1/**");
        // 高危操作二次确认：只约束服务账号的删除请求（ADR-030 P2）
        registry.addInterceptor(new ConfirmationInterceptor(confirmations, serviceAccounts))
                .addPathPatterns("/api/v1/**");
    }
}
