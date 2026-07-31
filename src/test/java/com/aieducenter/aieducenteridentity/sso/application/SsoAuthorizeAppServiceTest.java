package com.aieducenter.aieducenteridentity.sso.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.aieducenter.aieducenteridentity.sso.application.dto.AuthorizeRequest;
import com.aieducenter.aieducenteridentity.sso.config.SsoProperties;
import com.aieducenter.aieducenteridentity.sso.domain.client.SsoClient;
import com.aieducenter.aieducenteridentity.sso.domain.client.SsoClientValidationService;
import com.aieducenter.aieducenteridentity.sso.domain.session.SsoSession;
import com.aieducenter.aieducenteridentity.sso.domain.session.SsoSessionRepository;

class SsoAuthorizeAppServiceTest {

    private static final String CLIENT_ID = "demo-client";
    private static final String REDIRECT_URI = "https://demo.localhost/auth/callback";

    private final SsoClientValidationService clientValidation = mock(SsoClientValidationService.class);
    private final SsoSessionRepository sessionRepository = mock(SsoSessionRepository.class);
    private final AuthorizationCodeAppService codeService = mock(AuthorizationCodeAppService.class);
    private final SsoProperties properties = new SsoProperties();

    private final SsoAuthorizeAppService service =
        new SsoAuthorizeAppService(clientValidation, sessionRepository, codeService, properties);

    private final SsoClient client = new SsoClient(CLIENT_ID, "Demo", "hash",
        java.util.Set.of(REDIRECT_URI), java.util.Set.of("openid"), java.util.Set.of("authorization_code"), true);

    @BeforeEach
    void stubClient() {
        when(clientValidation.requireActiveClient(CLIENT_ID)).thenReturn(client);
    }

    @Test
    void given_no_session_when_authorize_then_redirect_to_login_page_with_params() {
        String location = service.handleAuthorize(
            new AuthorizeRequest(CLIENT_ID, REDIRECT_URI, "st@te", "non@ce", "openid profile"), null);

        assertThat(location).startsWith(properties.getLoginPageUrl());
        assertThat(location).contains("client_id=" + url(CLIENT_ID));
        assertThat(location).contains("redirect_uri=" + url(REDIRECT_URI));
        assertThat(location).contains("state=" + url("st@te"));
        assertThat(location).contains("nonce=" + url("non@ce"));
    }

    @Test
    void given_valid_session_when_authorize_then_issue_code_and_redirect_to_client() {
        SsoSession session = new SsoSession("sess", 700L, "阿福",
            java.time.Instant.now(), java.time.Instant.now().plusSeconds(3600));
        when(sessionRepository.findActive("sess")).thenReturn(Optional.of(session));
        when(codeService.issueCodeAndRedirect(eq(700L), eq(client), eq(REDIRECT_URI), eq("non@ce"), eq("openid"), eq("st")))
            .thenReturn(REDIRECT_URI + "?code=ABC&state=st");

        String location = service.handleAuthorize(
            new AuthorizeRequest(CLIENT_ID, REDIRECT_URI, "st", "non@ce", "openid"), "sess");

        assertThat(location).isEqualTo(REDIRECT_URI + "?code=ABC&state=st");
    }

    private static String url(String v) {
        return java.net.URLEncoder.encode(v, java.nio.charset.StandardCharsets.UTF_8);
    }
}
