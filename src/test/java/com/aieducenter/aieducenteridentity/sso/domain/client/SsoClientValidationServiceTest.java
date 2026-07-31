package com.aieducenter.aieducenteridentity.sso.domain.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.aieducenter.aieducenteridentity.sso.domain.error.OidcException;
import com.aieducenter.aieducenteridentity.sso.domain.error.SsoError;

class SsoClientValidationServiceTest {

    private static final String CLIENT_ID = "demo-client";
    private static final String REDIRECT_URI = "https://demo.localhost/auth/callback";

    private final SsoClientRepository clientRepository = mock(SsoClientRepository.class);
    private final SsoClientValidationService service = new SsoClientValidationService(clientRepository);

    private final SsoClient client = new SsoClient(CLIENT_ID, "Demo", "hash",
        java.util.Set.of(REDIRECT_URI), java.util.Set.of("openid"), java.util.Set.of("authorization_code"), true);

    @Test
    void given_registered_active_client_when_requireActiveClient_then_returned() {
        when(clientRepository.findByClientId(CLIENT_ID)).thenReturn(Optional.of(client));
        assertThat(service.requireActiveClient(CLIENT_ID)).isSameAs(client);
    }

    @Test
    void given_missing_client_id_when_requireActiveClient_then_unauthorized_client() {
        assertThatThrownBy(() -> service.requireActiveClient(null))
            .isInstanceOf(OidcException.class)
            .extracting(ex -> ((OidcException) ex).error())
            .isEqualTo(SsoError.UNAUTHORIZED_CLIENT);
    }

    @Test
    void given_unknown_client_id_when_requireActiveClient_then_unauthorized_client() {
        assertThatThrownBy(() -> service.requireActiveClient("ghost"))
            .isInstanceOf(OidcException.class)
            .extracting(ex -> ((OidcException) ex).error())
            .isEqualTo(SsoError.UNAUTHORIZED_CLIENT);
    }

    @Test
    void given_whitelisted_redirect_when_requireRedirectUri_then_ok() {
        service.requireRedirectUri(client, REDIRECT_URI); // no exception
    }

    @Test
    void given_missing_redirect_when_requireRedirectUri_then_invalid_request() {
        assertThatThrownBy(() -> service.requireRedirectUri(client, null))
            .isInstanceOf(OidcException.class)
            .extracting(ex -> ((OidcException) ex).error())
            .isEqualTo(SsoError.INVALID_REQUEST);
    }

    @Test
    void given_redirect_not_whitelisted_when_requireRedirectUri_then_invalid_request_no_redirect() {
        assertThatThrownBy(() -> service.requireRedirectUri(client, "https://evil.example/callback"))
            .isInstanceOf(OidcException.class)
            .extracting(ex -> ((OidcException) ex).error())
            .isEqualTo(SsoError.INVALID_REQUEST);
    }
}
