package xyz.byqy.vane.bootstrap.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 契约元信息（ADR-030 契约层）。
 *
 * <p>契约端点需认证后才能访问（不在安全放行清单内），接口面不对未认证方暴露。</p>
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI vaneOpenApi() {
        return new OpenAPI().info(new Info()
                .title("vane API")
                .version("0.1.0")
                .description("vane 前后台管理模板接口契约。错误体遵循 RFC 9457 Problem Details，"
                        + "错误码格式为「域.资源.原因」。")
                .license(new License().name("MIT")));
    }
}
