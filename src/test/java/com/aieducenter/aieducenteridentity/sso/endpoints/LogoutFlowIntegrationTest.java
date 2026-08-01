package com.aieducenter.aieducenteridentity.sso.endpoints;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import com.aieducenter.aieducenteridentity.account.domain.aggregate.Account;
import com.aieducenter.aieducenteridentity.account.domain.repository.AccountRepository;
import com.aieducenter.aieducenteridentity.sso.domain.session.SsoSession;

import jakarta.servlet.http.Cookie;

/**
 * {@code GET /logout}（RP-initiated logout）HTTP 黑盒集成测试（issue #19 AC）。
 *
 * <p>覆盖：白名单 redirect 302+state+清 cookie、无 redirect 200、非白名单 redirect 不重定向（防开放重定向）、
 * 无 cookie 登出正常、登出后 /authorize 不再免登。</p>
 */
@Transactional
class LogoutFlowIntegrationTest extends SsoIntegrationTestBase {

    @Autowired
    private AccountRepository accountRepository;

    private Cookie login(Long userId) {
        SsoSession session = createSsoSession(userId, "用户");
        return ssoCookie(session.sessionId());
    }

    private Long createUser() {
        return accountRepository.save(Account.register(null, "13900800001", null)).getId();
    }

    @Test
    void given_sso_cookie_when_logout_with_whitelisted_redirect_then_302_state_and_cookie_cleared() throws Exception {
        Long userId = createUser();
        Cookie cookie = login(userId);

        MvcResult result = mvc.perform(get("/logout")
                .param("client_id", CLIENT_ID)
                .param("post_logout_redirect_uri", REDIRECT_URI)
                .param("state", "xyz")
                .cookie(cookie))
            .andExpect(status().isFound())
            .andExpect(header().string("Location", REDIRECT_URI + "?state=xyz"))
            .andReturn();

        // SSO cookie 被清除（maxAge=0）
        assertThat(result.getResponse().getHeader("Set-Cookie")).contains("Max-Age=0");
        // 会话已删 → /authorize 不再免登（302 登录页）
        mvc.perform(get("/authorize")
                .param("client_id", CLIENT_ID)
                .param("redirect_uri", REDIRECT_URI)
                .cookie(cookie))
            .andExpect(status().isFound())
            .andExpect(header().string("Location", startsWith(ssoProperties.getLoginPageUrl())));
    }

    @Test
    void given_logout_when_no_post_logout_redirect_then_200_and_session_cleared() throws Exception {
        Long userId = createUser();
        Cookie cookie = login(userId);

        mvc.perform(get("/logout").param("client_id", CLIENT_ID).cookie(cookie))
            .andExpect(status().isOk());

        // 会话已删 → /authorize 不再免登
        mvc.perform(get("/authorize")
                .param("client_id", CLIENT_ID)
                .param("redirect_uri", REDIRECT_URI)
                .cookie(cookie))
            .andExpect(status().isFound())
            .andExpect(header().string("Location", startsWith(ssoProperties.getLoginPageUrl())));
    }

    @Test
    void given_logout_when_redirect_not_whitelisted_then_200_no_redirect_but_session_cleared() throws Exception {
        Long userId = createUser();
        Cookie cookie = login(userId);

        // post_logout_redirect_uri 不在白名单 → 不重定向（防开放重定向），但仍清会话
        mvc.perform(get("/logout")
                .param("client_id", CLIENT_ID)
                .param("post_logout_redirect_uri", "https://evil.example/logout-cb")
                .cookie(cookie))
            .andExpect(status().isOk());

        // 会话仍被清 → /authorize 不再免登
        mvc.perform(get("/authorize")
                .param("client_id", CLIENT_ID)
                .param("redirect_uri", REDIRECT_URI)
                .cookie(cookie))
            .andExpect(status().isFound())
            .andExpect(header().string("Location", startsWith(ssoProperties.getLoginPageUrl())));
    }

    @Test
    void given_logout_when_client_id_unknown_then_200_no_redirect_but_session_cleared() throws Exception {
        Long userId = createUser();
        Cookie cookie = login(userId);

        // client_id 未知 → 无法校验白名单 → 不重定向，但仍清会话
        mvc.perform(get("/logout")
                .param("client_id", "ghost-client")
                .param("post_logout_redirect_uri", REDIRECT_URI)
                .cookie(cookie))
            .andExpect(status().isOk());

        mvc.perform(get("/authorize")
                .param("client_id", CLIENT_ID)
                .param("redirect_uri", REDIRECT_URI)
                .cookie(cookie))
            .andExpect(status().isFound())
            .andExpect(header().string("Location", startsWith(ssoProperties.getLoginPageUrl())));
    }

    @Test
    void given_no_sso_cookie_when_logout_with_whitelisted_redirect_then_302_no_error() throws Exception {
        // 无 cookie 登出亦正常（登出优先），redirect 仍按白名单放行
        mvc.perform(get("/logout")
                .param("client_id", CLIENT_ID)
                .param("post_logout_redirect_uri", REDIRECT_URI))
            .andExpect(status().isFound())
            .andExpect(header().string("Location", REDIRECT_URI));
    }
}
