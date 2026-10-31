package xyz.byqy.vane.bootstrap.config;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import xyz.byqy.vane.bootstrap.idempotency.IdempotencyFilter;
import xyz.byqy.vane.bootstrap.idempotency.IdempotencyInterceptor;
import xyz.byqy.vane.system.application.IdempotencyStore;

/**
 * 幂等执行的装配（ADR-030 执行层）。
 *
 * <p>过滤器排在安全链之后，保证操作者身份可读；拦截器只处理带幂等键的创建类请求。</p>
 */
@Configuration
public class IdempotencyWebConfig implements WebMvcConfigurer {

    private final IdempotencyStore store;
    private final Duration ttl;
    private final int maxBodyBytes;

    public IdempotencyWebConfig(IdempotencyStore store,
                                @Value("${vane.idempotency.ttl:PT24H}") Duration ttl,
                                @Value("${vane.idempotency.max-body-bytes:1048576}") int maxBodyBytes) {
        this.store = store;
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

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new IdempotencyInterceptor(store, ttl)).addPathPatterns("/api/v1/**");
    }
}
