package com.aieducenter.aieducenteridentity.sso.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

/**
 * local profile SSO 配置守护（issue #27）：login-page-url 必须指向 identity-web 登录页；
 * cookie-secure 必须 false（issue #36，.localhost 下 Safari/Firefox 不豁免）。
 */
class ApplicationLocalYmlTest {

    private PropertySource<?> loadLocalYml() throws IOException {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader().load(
            "application-local.yml", new ClassPathResource("application-local.yml"));
        return sources.get(0);
    }

    @Test
    void given_local_profile_when_read_login_page_url_then_points_to_identity_web() throws IOException {
        assertThat(loadLocalYml().getProperty("identity.sso.login-page-url"))
            .isEqualTo("http://identity.localhost:10002/login");
    }

    @Test
    void given_local_profile_when_read_cookie_secure_then_false() throws IOException {
        // issue #36：local 跑 http://*.localhost，Safari/Firefox 不豁免 Secure-over-http（仅 Chrome/Edge 豁免），
        // Secure cookie 会被拒存 → sso_session 落不了地、SSO 会话不持久。故 local 必须关 Secure；prod 走 https 仍 true。
        assertThat(loadLocalYml().getProperty("identity.sso.cookie-secure"))
            .isEqualTo(false);
    }
}
