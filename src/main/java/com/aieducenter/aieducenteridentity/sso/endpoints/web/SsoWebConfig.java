package com.aieducenter.aieducenteridentity.sso.endpoints.web;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.aieducenter.aieducenteridentity.sso.config.SsoProperties;
import com.aieducenter.aieducenteridentity.sso.domain.session.SsoSessionRepository;

/**
 * SSO web 层装配——注册 {@link SsoSessionFilter}。
 *
 * <p>经 {@link FilterRegistrationBean} 注册（而非 {@code @Component}），使 {@code @WebMvcTest} 切片不会扫描到
 * 过滤器（其依赖的 {@link SsoSessionRepository} 不在切片内，会致上下文加载失败）。全应用上下文
 * （{@code @SpringBootTest} / 正式运行）正常装载。</p>
 *
 * @since 0.1.0
 */
@Configuration
public class SsoWebConfig {

    @Bean
    public FilterRegistrationBean<SsoSessionFilter> ssoSessionFilterRegistration(
            SsoSessionRepository sessionRepository, SsoCookieService cookieService, SsoProperties properties) {
        FilterRegistrationBean<SsoSessionFilter> registration =
            new FilterRegistrationBean<>(new SsoSessionFilter(sessionRepository, cookieService, properties));
        registration.setOrder(SsoSessionFilter.ORDER);
        registration.addUrlPatterns("/*");
        return registration;
    }
}
