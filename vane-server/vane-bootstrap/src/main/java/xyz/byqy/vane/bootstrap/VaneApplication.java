package xyz.byqy.vane.bootstrap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

// 认证走自研的 Bearer 过滤器（不透明令牌 + 自定义 SecurityFilterChain），没有 UserDetailsService；
// 排除官方默认配置，避免启动时创建无用的内存用户并打印「generated security password」。
@SpringBootApplication(scanBasePackages = "xyz.byqy.vane", exclude = UserDetailsServiceAutoConfiguration.class)
@EntityScan(basePackages = "xyz.byqy.vane")
@EnableJpaRepositories(basePackages = "xyz.byqy.vane")
public class VaneApplication {

    public static void main(String[] args) {
        SpringApplication.run(VaneApplication.class, args);
    }
}

