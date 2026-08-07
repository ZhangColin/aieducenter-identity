package com.aieducenter.demobff.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

/**
 * local profile SSO 配置守护。
 */
class ApplicationLocalYmlTest {

    private PropertySource<?> loadLocalYml() throws IOException {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader().load(
            "application-local.yml", new ClassPathResource("application-local.yml"));
        return sources.get(0);
    }

    @Test
    void given_local_profile_when_read_cookie_secure_then_false() throws IOException {
        // issue #37：local 跑 http://*.localhost，Safari/Firefox 不豁免 Secure-over-http（仅 Chrome/Edge 豁免），
        // Secure cookie 会被拒存 → oauth_txn 落不了地 → callback 恒 state_mismatch；demo_session 也种不上。
        // 故 local 必须关 Secure；prod 走 https 仍用默认 true。
        assertThat(loadLocalYml().getProperty("sso.cookie-secure"))
            .isEqualTo(false);
    }
}
