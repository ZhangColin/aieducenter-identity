package com.aieducenter.aieducenteridentity.sso.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.util.Map;

class SsoPropertiesTest {

    private SsoProperties bind(Map<String, String> source) {
        return new Binder(new MapConfigurationPropertySource(source))
            .bind("identity.sso", SsoProperties.class).get();
    }

    @Test
    void devLogin_disabled_by_default() {
        assertThat(new SsoProperties().getDevLogin().isEnabled()).isFalse();
    }

    @Test
    void devLogin_binds_from_relaxed_names() {
        SsoProperties p = bind(Map.of(
            "identity.sso.dev-login.enabled", "true",
            "identity.sso.dev-login.account-email", "demo@aieducenter.com",
            "identity.sso.dev-login.account-password", "demo12345",
            "identity.sso.dev-login.account-nickname", "Demo 用户"));
        assertThat(p.getDevLogin().isEnabled()).isTrue();
        assertThat(p.getDevLogin().getAccountEmail()).isEqualTo("demo@aieducenter.com");
        assertThat(p.getDevLogin().getAccountNickname()).isEqualTo("Demo 用户");
    }
}
