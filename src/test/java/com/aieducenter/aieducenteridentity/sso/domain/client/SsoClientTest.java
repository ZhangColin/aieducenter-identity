package com.aieducenter.aieducenteridentity.sso.domain.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.Test;

class SsoClientTest {

    private final SsoClient client = new SsoClient("demo", "Demo",
        "hash", Set.of("https://demo.localhost/auth/callback"),
        Set.of("https://demo.localhost/"),
        Set.of("openid"), Set.of("authorization_code", "refresh_token"), true);

    @Test
    void given_whitelisted_uri_when_hasRedirectUri_then_true() {
        assertThat(client.hasRedirectUri("https://demo.localhost/auth/callback")).isTrue();
    }

    @Test
    void given_unknown_uri_when_hasRedirectUri_then_false() {
        // 精确匹配，非前缀
        assertThat(client.hasRedirectUri("https://demo.localhost/auth/callback/extra")).isFalse();
        assertThat(client.hasRedirectUri("https://evil.example/callback")).isFalse();
        assertThat(client.hasRedirectUri(null)).isFalse();
    }

    @Test
    void given_null_post_logout_uris_when_construct_then_empty_set() {
        // app-registry 未下发该字段时 null 兜底为空集（ADR-0005）；空集 = 不放行任何登出回跳
        SsoClient nullPostLogout = new SsoClient("demo", "Demo", "hash",
            Set.of("https://demo.localhost/auth/callback"), null,
            Set.of("openid"), Set.of("authorization_code"), true);
        assertThat(nullPostLogout.postLogoutRedirectUris()).isEmpty();
    }

    @Test
    void given_whitelisted_post_logout_uri_when_hasPostLogoutRedirectUri_then_true() {
        assertThat(client.hasPostLogoutRedirectUri("https://demo.localhost/")).isTrue();
    }

    @Test
    void given_unknown_post_logout_uri_when_hasPostLogoutRedirectUri_then_false() {
        // 精确匹配，非前缀；登录回调不属登出白名单（两白名单独立，ADR-0005）
        assertThat(client.hasPostLogoutRedirectUri("https://demo.localhost/auth/callback")).isFalse();
        assertThat(client.hasPostLogoutRedirectUri("https://demo.localhost/dashboard")).isFalse();
        assertThat(client.hasPostLogoutRedirectUri(null)).isFalse();
    }

    @Test
    void given_grant_when_supportsGrant_then_matches_membership() {
        assertThat(client.supportsGrant("authorization_code")).isTrue();
        assertThat(client.supportsGrant("refresh_token")).isTrue();
        assertThat(client.supportsGrant("password")).isFalse();
    }
}
