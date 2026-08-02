package com.aieducenter.aieducenteridentity.sso.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

/**
 * local profile SSO 配置守护（issue #27）：login-page-url 必须指向 identity-web 登录页，
 * 防回退到 dev 一键登（dev-login 仅保留作 identity-web 缺席时的手工兜底）。
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
    void given_local_profile_when_read_dev_login_enabled_then_stays_true_as_manual_fallback() throws IOException {
        assertThat(loadLocalYml().getProperty("identity.sso.dev-login.enabled"))
            .isEqualTo(true);
    }
}
