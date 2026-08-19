package com.aieducenter.aieducenteridentity.account.endpoints.web;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.aieducenter.aieducenteridentity.account.config.ManagementProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 装配 {@link ManagementCallerInterceptor}（ADR-0010 / #67）——经 {@code @Bean} 注册（非 {@code @Component}），
 * 与 {@code SsoWebConfig} 同模式：使 {@code @WebMvcTest} 切片不扫描到本配置（拦截器依赖的
 * {@link ManagementProperties} 不在切片内，会致上下文加载失败）。全应用上下文
 * （{@code @SpringBootTest} / 正式运行）正常装载。
 *
 * <p>注册 order 在 cartisan-openapi 的 {@code SignatureVerificationInterceptor}（默认 order 0）<b>之后</b>
 * ——签名 gate 先判认证（401），本 gate 再判管理白名单（403）。与 {@link ManagementCallerInterceptor}
 * 内「验签 attribute 缺失放行、交签名 gate 回 401」的兜底配合，401 / 403 区分不依赖拦截器顺序。</p>
 *
 * @since 0.1.0
 */
@Configuration
public class ManagementWebConfig {

    @Bean
    ManagementCallerInterceptor managementCallerInterceptor(ObjectMapper objectMapper, ManagementProperties properties) {
        return new ManagementCallerInterceptor(objectMapper, properties);
    }

    /**
     * 注册管理调用方 gate 拦截器——order=1（在签名 interceptor 默认 order 0 之后），path {@code /**}
     * （拦截器内按 {@link RequireManagementCaller} 注解决定是否真正 gate）。
     */
    @Bean
    WebMvcConfigurer managementCallerInterceptorRegistration(ManagementCallerInterceptor interceptor) {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(interceptor)
                    .addPathPatterns("/**")
                    .order(1);
            }
        };
    }
}
