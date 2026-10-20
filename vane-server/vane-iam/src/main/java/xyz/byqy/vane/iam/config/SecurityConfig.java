package xyz.byqy.vane.iam.config;

import java.io.IOException;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import xyz.byqy.vane.common.error.CommonErrors;
import xyz.byqy.vane.common.error.ErrorCode;
import xyz.byqy.vane.common.problem.ProblemDetailWriter;
import xyz.byqy.vane.iam.api.security.BearerAuthenticationFilter;

/**
 * 安全基线（03 §14、06 §3–§4）：
 * - 无状态、无 Basic/Form 登录、CSRF 关闭（Bearer 传输，非 Cookie 认证）
 * - 认证失败与拒绝访问一律输出 Problem Details
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final String[] PUBLIC_PATHS = {
            "/actuator/health/**",
            "/actuator/health",
            "/actuator/info",
            "/api/v1/auth/login",
            "/api/v1/auth/refresh",
            "/api/v1/auth/logout",
            "/api/v1/auth/mfa/verify",
            "/api/v1/auth/sms-code/send",
            "/api/v1/auth/sms-code/verify",
            "/api/v1/public/**",
            "/sitemap.xml",
            "/robots.txt",
            "/error"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   BearerAuthenticationFilter bearerFilter,
                                                   ProblemDetailWriter problemWriter) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(bearerFilter, UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint((request, response, ex) ->
                                writeProblem(problemWriter, request, response, CommonErrors.AUTH_TOKEN_MISSING))
                        .accessDeniedHandler((request, response, ex) ->
                                writeProblem(problemWriter, request, response, CommonErrors.AUTH_FORBIDDEN)));
        return http.build();
    }

    private static void writeProblem(ProblemDetailWriter writer, HttpServletRequest request,
                                     HttpServletResponse response, ErrorCode code) throws IOException {
        writer.write(request, response, code);
    }
}



