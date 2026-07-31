package com.aieducenter.aieducenteridentity.sso.domain.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.Test;

class SsoClientTest {

    private final SsoClient client = new SsoClient("demo", "Demo",
        "hash", Set.of("https://demo.localhost/auth/callback"),
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
    void given_grant_when_supportsGrant_then_matches_membership() {
        assertThat(client.supportsGrant("authorization_code")).isTrue();
        assertThat(client.supportsGrant("refresh_token")).isTrue();
        assertThat(client.supportsGrant("password")).isFalse();
    }
}
