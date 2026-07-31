package com.aieducenter.aieducenteridentity.sso.infrastructure.client;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.aieducenter.aieducenteridentity.sso.config.SsoProperties;
import com.aieducenter.aieducenteridentity.sso.domain.client.SsoClient;

class StubSsoClientRepositoryAdapterTest {

    private final SsoProperties properties = new SsoProperties();
    private final StubSsoClientRepositoryAdapter repository = new StubSsoClientRepositoryAdapter(properties);

    @Test
    void given_configured_stub_client_id_when_findByClientId_then_present_and_active() {
        SsoClient client = repository.findByClientId(properties.getStubClientId()).orElseThrow();

        assertThat(client.active()).isTrue();
        assertThat(client.clientSecretHash()).isNotBlank().isNotEqualTo(properties.getStubClientSecret());
        assertThat(client.hasRedirectUri("https://demo.localhost/auth/callback")).isTrue();
        assertThat(client.supportsGrant("authorization_code")).isTrue();
        assertThat(client.supportsGrant("refresh_token")).isTrue();
    }

    @Test
    void given_unknown_client_id_when_findByClientId_then_empty() {
        assertThat(repository.findByClientId("nope")).isEmpty();
        assertThat(repository.findByClientId(null)).isEmpty();
    }
}
