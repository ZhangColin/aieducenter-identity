package com.aieducenter.aieducenteridentity.sso.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.aieducenter.aieducenteridentity.account.application.TokenIssuerAppService;
import com.aieducenter.aieducenteridentity.account.application.dto.response.LoginResponse;
import com.aieducenter.aieducenteridentity.account.domain.aggregate.Account;
import com.aieducenter.aieducenteridentity.account.domain.enums.AccountStatus;
import com.aieducenter.aieducenteridentity.account.domain.repository.AccountRepository;
import com.aieducenter.aieducenteridentity.account.domain.repository.ProfileRepository;
import com.aieducenter.aieducenteridentity.sso.application.dto.TokenRequest;
import com.aieducenter.aieducenteridentity.sso.application.dto.TokenResponse;
import com.aieducenter.aieducenteridentity.sso.domain.client.ClientSecretVerifier;
import com.aieducenter.aieducenteridentity.sso.domain.client.SsoClient;
import com.aieducenter.aieducenteridentity.sso.domain.client.SsoClientRepository;
import com.aieducenter.aieducenteridentity.sso.domain.code.AuthorizationCodeStore;
import com.aieducenter.aieducenteridentity.sso.domain.code.IssuedAuthorizationCode;
import com.aieducenter.aieducenteridentity.sso.domain.error.OidcException;
import com.aieducenter.aieducenteridentity.sso.domain.error.SsoError;

class SsoTokenAppServiceTest {

    private static final String CLIENT_ID = "demo-client";
    private static final String SECRET = "demo-secret";
    private static final String REDIRECT_URI = "https://demo.localhost/auth/callback";
    private static final String NONCE = "nonce-xyz";
    private static final long USER_ID = 500L;

    private final SsoClientRepository clientRepository = mock(SsoClientRepository.class);
    private final ClientSecretVerifier secretVerifier = mock(ClientSecretVerifier.class);
    private final AuthorizationCodeStore codeStore = mock(AuthorizationCodeStore.class);
    private final TokenIssuerAppService tokenIssuer = mock(TokenIssuerAppService.class);
    private final AccountRepository accountRepository = mock(AccountRepository.class);
    private final ProfileRepository profileRepository = mock(ProfileRepository.class);

    private final SsoTokenAppService service = new SsoTokenAppService(
        clientRepository, secretVerifier, codeStore, tokenIssuer, accountRepository, profileRepository);

    private final SsoClient client = new SsoClient(CLIENT_ID, "Demo", "hash",
        java.util.Set.of(REDIRECT_URI), java.util.Set.of("openid"),
        java.util.Set.of("authorization_code", "refresh_token"), true);

    @BeforeEach
    void stubClient() {
        when(clientRepository.findByClientId(CLIENT_ID)).thenReturn(Optional.of(client));
        when(secretVerifier.matches(SECRET, "hash")).thenReturn(true);
        when(accountRepository.findById(USER_ID)).thenReturn(Optional.of(
            Account.restore(USER_ID, "u@test.com", null, "pwhash", AccountStatus.ACTIVE, false, null)));
        when(tokenIssuer.issue(any(), any(), any(), any())).thenReturn(
            LoginResponse.of("access-jwt", "refresh-jwt", "id-jwt", 900));
    }

    // ── code grant ─────────────────────────────────────────────────────────────

    @Test
    void given_code_grant_when_token_then_issue_tokens_with_nonce() {
        when(codeStore.consume("CODE")).thenReturn(Optional.of(
            new IssuedAuthorizationCode(CLIENT_ID, REDIRECT_URI, USER_ID, NONCE, "openid")));

        TokenResponse response = service.token(new TokenRequest(
            "authorization_code", "CODE", REDIRECT_URI, CLIENT_ID, SECRET, null));

        assertThat(response.accessToken()).isEqualTo("access-jwt");
        assertThat(response.idToken()).isEqualTo("id-jwt");
        assertThat(response.refreshToken()).isEqualTo("refresh-jwt");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        // nonce 透传给 TokenIssuerAppService 写入 id_token；scope 透传写入 access_token（issue #17）
        verify(tokenIssuer).issue(any(), any(), eq(NONCE), eq("openid"));
    }

    @Test
    void given_wrong_client_secret_when_token_then_invalid_client() {
        assertThatThrownBy(() -> service.token(new TokenRequest(
            "authorization_code", "CODE", REDIRECT_URI, CLIENT_ID, "wrong", null)))
            .isInstanceOf(OidcException.class)
            .extracting(ex -> ((OidcException) ex).error())
            .isEqualTo(SsoError.INVALID_CLIENT);
    }

    @Test
    void given_already_consumed_code_when_token_then_invalid_grant() {
        when(codeStore.consume("CODE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.token(new TokenRequest(
            "authorization_code", "CODE", REDIRECT_URI, CLIENT_ID, SECRET, null)))
            .isInstanceOf(OidcException.class)
            .extracting(ex -> ((OidcException) ex).error())
            .isEqualTo(SsoError.INVALID_GRANT);
    }

    @Test
    void given_code_bound_to_other_client_when_token_then_invalid_grant() {
        when(codeStore.consume("CODE")).thenReturn(Optional.of(
            new IssuedAuthorizationCode("other-client", REDIRECT_URI, USER_ID, NONCE, null)));

        assertThatThrownBy(() -> service.token(new TokenRequest(
            "authorization_code", "CODE", REDIRECT_URI, CLIENT_ID, SECRET, null)))
            .isInstanceOf(OidcException.class)
            .extracting(ex -> ((OidcException) ex).error())
            .isEqualTo(SsoError.INVALID_GRANT);
    }

    @Test
    void given_redirect_uri_mismatch_when_token_then_invalid_grant() {
        when(codeStore.consume("CODE")).thenReturn(Optional.of(
            new IssuedAuthorizationCode(CLIENT_ID, REDIRECT_URI, USER_ID, NONCE, null)));

        assertThatThrownBy(() -> service.token(new TokenRequest(
            "authorization_code", "CODE", "https://evil.example/callback", CLIENT_ID, SECRET, null)))
            .isInstanceOf(OidcException.class)
            .extracting(ex -> ((OidcException) ex).error())
            .isEqualTo(SsoError.INVALID_GRANT);
    }

    @Test
    void given_unsupported_grant_type_when_token_then_unsupported_grant_type() {
        assertThatThrownBy(() -> service.token(new TokenRequest(
            "password", null, null, CLIENT_ID, SECRET, null)))
            .isInstanceOf(OidcException.class)
            .extracting(ex -> ((OidcException) ex).error())
            .isEqualTo(SsoError.UNSUPPORTED_GRANT_TYPE);
    }

    // ── refresh grant ──────────────────────────────────────────────────────────

    @Test
    void given_refresh_grant_when_token_then_issue_new_tokens_no_nonce() {
        when(tokenIssuer.consumeRefresh("RT")).thenReturn(Optional.of(USER_ID));

        TokenResponse response = service.token(new TokenRequest(
            "refresh_token", null, null, CLIENT_ID, SECRET, "RT"));

        assertThat(response.accessToken()).isEqualTo("access-jwt");
        assertThat(response.refreshToken()).isEqualTo("refresh-jwt");
        // refresh grant 无 nonce / 无 scope（scope 留在 code grant，refresh 不携带）
        verify(tokenIssuer).issue(any(), any(), eq(null), eq(null));
    }

    @Test
    void given_invalid_refresh_when_token_then_invalid_grant() {
        when(tokenIssuer.consumeRefresh("RT")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.token(new TokenRequest(
            "refresh_token", null, null, CLIENT_ID, SECRET, "RT")))
            .isInstanceOf(OidcException.class)
            .extracting(ex -> ((OidcException) ex).error())
            .isEqualTo(SsoError.INVALID_GRANT);
    }

    @Test
    void given_disabled_account_when_refresh_then_invalid_grant() {
        when(accountRepository.findById(USER_ID)).thenReturn(Optional.of(
            Account.restore(USER_ID, "u@test.com", null, "pwhash", AccountStatus.DISABLED, false, null)));
        when(tokenIssuer.consumeRefresh("RT")).thenReturn(Optional.of(USER_ID));

        assertThatThrownBy(() -> service.token(new TokenRequest(
            "refresh_token", null, null, CLIENT_ID, SECRET, "RT")))
            .isInstanceOf(OidcException.class)
            .extracting(ex -> ((OidcException) ex).error())
            .isEqualTo(SsoError.INVALID_GRANT);
    }
}
