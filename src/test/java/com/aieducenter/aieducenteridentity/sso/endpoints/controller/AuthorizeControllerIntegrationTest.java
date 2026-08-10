package com.aieducenter.aieducenteridentity.sso.endpoints.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import com.aieducenter.aieducenteridentity.sso.endpoints.SsoIntegrationTestBase;
import com.aieducenter.aieducenteridentity.sso.domain.session.SsoSession;

/**
 * {@code GET /authorize} HTTP 黑盒集成测试（issue #15 AC）。
 *
 * <p>覆盖：无 SSO cookie → 302 登录页透传参数；有 SSO cookie → 302 直发 code；redirect_uri 不匹配 → 400；
 * 未知 client → 400。</p>
 */
class AuthorizeControllerIntegrationTest extends SsoIntegrationTestBase {

    private static final String STATE = "csrf-state-123";
    private static final String NONCE = "replay-nonce-456";

    @Test
    void given_no_sso_cookie_when_authorize_then_redirect_to_login_page_with_params() throws Exception {
        MvcResult result = mvc.perform(get("/authorize")
                .param("client_id", CLIENT_ID)
                .param("redirect_uri", REDIRECT_URI)
                .param("response_type", "code")
                .param("state", STATE)
                .param("nonce", NONCE)
                .param("scope", "openid profile"))
            .andExpect(status().isFound())
            .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith(ssoProperties.getLoginPageUrl())))
            .andReturn();

        String location = result.getResponse().getHeader("Location");
        assertThat(queryParam(location, "client_id")).isEqualTo(CLIENT_ID);
        assertThat(queryParam(location, "redirect_uri")).isEqualTo(REDIRECT_URI);
        assertThat(queryParam(location, "state")).isEqualTo(STATE);
        assertThat(queryParam(location, "nonce")).isEqualTo(NONCE);
    }

    @Test
    void given_valid_sso_cookie_when_authorize_then_issue_code_and_redirect_to_client() throws Exception {
        SsoSession session = createSsoSession(800L, "免登用户");

        MvcResult result = mvc.perform(get("/authorize")
                .param("client_id", CLIENT_ID)
                .param("redirect_uri", REDIRECT_URI)
                .param("state", STATE)
                .param("nonce", NONCE)
                .cookie(ssoCookie(session.sessionId())))
            .andExpect(status().isFound())
            .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith(REDIRECT_URI + "?")))
            .andReturn();

        String location = result.getResponse().getHeader("Location");
        String code = queryParam(location, "code");
        assertThat(code).isNotBlank();
        assertThat(queryParam(location, "state")).isEqualTo(STATE);
    }

    @Test
    void given_redirect_uri_not_whitelisted_when_authorize_then_redirect_to_error_page_with_invalid_request() throws Exception {
        MvcResult result = mvc.perform(get("/authorize")
                .param("client_id", CLIENT_ID)
                .param("redirect_uri", "https://evil.example/callback")
                .param("state", STATE))
            .andExpect(status().isFound())
            .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith(ssoProperties.getErrorPageUrl())))
            .andReturn();

        String location = result.getResponse().getHeader("Location");
        assertThat(queryParam(location, "error")).isEqualTo("invalid_request");
        assertThat(queryParam(location, "client_id")).isEqualTo(CLIENT_ID);
    }

    @Test
    void given_unknown_client_when_authorize_then_redirect_to_error_page_with_unauthorized_client() throws Exception {
        MvcResult result = mvc.perform(get("/authorize")
                .param("client_id", "ghost-client")
                .param("redirect_uri", REDIRECT_URI)
                .param("state", STATE))
            .andExpect(status().isFound())
            .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith(ssoProperties.getErrorPageUrl())))
            .andReturn();

        String location = result.getResponse().getHeader("Location");
        assertThat(queryParam(location, "error")).isEqualTo("unauthorized_client");
        assertThat(queryParam(location, "client_id")).isEqualTo("ghost-client");
    }
}
