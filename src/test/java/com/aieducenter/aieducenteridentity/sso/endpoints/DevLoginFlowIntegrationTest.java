package com.aieducenter.aieducenteridentity.sso.endpoints;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import com.aieducenter.aieducenteridentity.account.domain.repository.AccountRepository;

/**
 * dev 一键登闭环（issue #16）：identity-web 缺席时 /authorize 无 cookie 跳 dev-login，
 * dev-login 自动登预置测试账号 → 发 code → /token 换 token；二次凭 cookie 免登。
 * 打开 dev-login 开关后 seeder 已在上下文启动时种入 demo@aieducenter.com。
 */
@Transactional
@DirtiesContext
@TestPropertySource(properties = {
    "identity.sso.dev-login.enabled=true",
    "identity.sso.login-page-url=http://localhost/api/auth/dev-login",
    "identity.sso.stub-redirect-uris[0]=http://demo.localhost:3000/auth/callback"
})
class DevLoginFlowIntegrationTest extends SsoIntegrationTestBase {

    private static final String REDIRECT = "http://demo.localhost:3000/auth/callback";

    @Autowired
    private AccountRepository accountRepository;

    @Test
    void authorize_without_cookie_dev_login_then_token() throws Exception {
        assertThat(accountRepository.findByEmail("demo@aieducenter.com")).isPresent();

        // 1. /authorize 无 cookie → 302 dev-login（透传 client_id/redirect_uri/state/nonce）
        MvcResult auth = mvc.perform(get("/authorize")
                .param("client_id", CLIENT_ID)
                .param("redirect_uri", REDIRECT)
                .param("state", "s1")
                .param("nonce", "n1"))
            .andExpect(status().isFound())
            .andExpect(header().string("Location",
                org.hamcrest.Matchers.startsWith("http://localhost/api/auth/dev-login")))
            .andReturn();
        String devLoginUrl = auth.getResponse().getHeader("Location");

        // 2. dev-login → Set-Cookie(SSO) + 302 redirect_uri?code&state
        // 注：devLoginUrl 来自 /authorize Location 头，redirect_uri 已 percent-encode；
        // MockMvc 的 get(String) 不解码 query，须用 get(URI) 让 servlet 正确 percent-decode。
        MvcResult devLogin = mvc.perform(get(URI.create(devLoginUrl)))
            .andExpect(status().isFound())
            .andExpect(header().exists("Set-Cookie"))
            .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith(REDIRECT + "?")))
            .andReturn();
        String sessionId = extractCookieValue(devLogin, ssoProperties.getCookieName());
        String code = queryParam(devLogin, "code");
        assertThat(queryParam(devLogin, "state")).isEqualTo("s1");

        // 3. /token code grant → access/id/refresh
        mvc.perform(post("/token").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("grant_type", "authorization_code").param("code", code)
                .param("redirect_uri", REDIRECT)
                .param("client_id", CLIENT_ID).param("client_secret", CLIENT_SECRET))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.access_token").isNotEmpty())
            .andExpect(jsonPath("$.id_token").isNotEmpty());

        // 4. 二次 /authorize 凭 cookie 免登直发 code
        Cookie sso = new Cookie(ssoProperties.getCookieName(), sessionId);
        mvc.perform(get("/authorize").param("client_id", CLIENT_ID)
                .param("redirect_uri", REDIRECT).param("state", "s2").cookie(sso))
            .andExpect(status().isFound())
            .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith(REDIRECT + "?")));
    }

    @Test
    void dev_login_rejects_unregistered_redirect_uri() throws Exception {
        mvc.perform(get("/api/auth/dev-login")
                .param("client_id", CLIENT_ID)
                .param("redirect_uri", "https://evil.example/cb"))
            .andExpect(status().isBadRequest());
    }
}
