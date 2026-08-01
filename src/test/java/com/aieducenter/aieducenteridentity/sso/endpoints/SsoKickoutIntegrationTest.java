package com.aieducenter.aieducenteridentity.sso.endpoints;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import com.aieducenter.aieducenteridentity.account.application.AccountPasswordAppService;
import com.aieducenter.aieducenteridentity.account.application.AccountStatusAppService;
import com.aieducenter.aieducenteridentity.account.application.dto.command.ChangePasswordCommand;
import com.aieducenter.aieducenteridentity.account.domain.aggregate.Account;
import com.aieducenter.aieducenteridentity.account.domain.repository.AccountRepository;
import com.aieducenter.aieducenteridentity.account.domain.service.AccountPasswordEncoderService;
import com.aieducenter.aieducenteridentity.sso.domain.session.SsoSession;
import com.cartisan.core.context.RequestContext;
import com.jayway.jsonpath.JsonPath;

import jakarta.servlet.http.Cookie;

/**
 * 改密/封号踢人 + 准 SLO HTTP 黑盒集成测试（issue #19 AC）。
 *
 * <p>改密/封号/锁定后该 userId 所有 SSO 会话失效（{@code /authorize} 不再免登）；登出清会话后 refresh 绑会话失效
 * （refresh grant → invalid_grant）——验证「登出/踢人后 ≤15min 全失效」的准 SLO。</p>
 */
@Transactional
class SsoKickoutIntegrationTest extends SsoIntegrationTestBase {

    private static final String PHONE = "13900900001";
    private static final String PASSWORD = "Password123";
    private static final String NEW_PASSWORD = "NewPass456";

    @Autowired
    private AccountRepository accountRepository;
    @Autowired
    private AccountPasswordEncoderService passwordEncoderService;
    @Autowired
    private AccountPasswordAppService passwordAppService;
    @Autowired
    private AccountStatusAppService statusAppService;

    private Long createAccount() {
        Account account = Account.register(null, PHONE, passwordEncoderService.encodePassword(PASSWORD));
        return accountRepository.save(account).getId();
    }

    private Cookie ssoLogin(Long userId) {
        SsoSession session = createSsoSession(userId, "用户");
        return ssoCookie(session.sessionId());
    }

    private void assertSsoEnabled(Cookie cookie) throws Exception {
        mvc.perform(get("/authorize").param("client_id", CLIENT_ID).param("redirect_uri", REDIRECT_URI).cookie(cookie))
            .andExpect(status().isFound())
            .andExpect(header().string("Location", startsWith(REDIRECT_URI + "?")));
    }

    private void assertSsoRevoked(Cookie cookie) throws Exception {
        mvc.perform(get("/authorize").param("client_id", CLIENT_ID).param("redirect_uri", REDIRECT_URI).cookie(cookie))
            .andExpect(status().isFound())
            .andExpect(header().string("Location", startsWith(ssoProperties.getLoginPageUrl())));
    }

    @Test
    void given_change_password_then_sso_session_revoked() throws Exception {
        Long userId = createAccount();
        Cookie cookie = ssoLogin(userId);
        assertSsoEnabled(cookie);

        // 改密（应用服务；RequestContext 设 userId 模拟登录态——改密 HTTP 入口 sa-token 链路 #21 切换）
        RequestContext context = new RequestContext(null, null, null, null, userId, "u", null, null);
        RequestContext.run(context, () -> passwordAppService.changePassword(
            new ChangePasswordCommand(PASSWORD, NEW_PASSWORD)));

        assertSsoRevoked(cookie);
    }

    @Test
    void given_disable_account_then_sso_session_revoked() throws Exception {
        Long userId = createAccount();
        Cookie cookie = ssoLogin(userId);
        assertSsoEnabled(cookie);

        statusAppService.disable(userId);

        assertSsoRevoked(cookie);
    }

    @Test
    void given_lock_account_then_sso_session_revoked() throws Exception {
        Long userId = createAccount();
        Cookie cookie = ssoLogin(userId);
        assertSsoEnabled(cookie);

        statusAppService.lock(userId);

        assertSsoRevoked(cookie);
    }

    @Test
    void given_logout_then_refresh_token_rejected() throws Exception {
        // 准 SLO：登出清会话后，refresh 绑会话失效 → invalid_grant
        Long userId = createAccount();
        Cookie cookie = ssoLogin(userId);

        // /authorize 拿 code → /token 换 refresh（refresh 绑该 SSO 会话）
        MvcResult auth = mvc.perform(get("/authorize").param("client_id", CLIENT_ID)
                .param("redirect_uri", REDIRECT_URI).param("state", "st").cookie(cookie))
            .andExpect(status().isFound()).andReturn();
        String code = queryParam(auth.getResponse().getHeader("Location"), "code");
        MvcResult token = mvc.perform(post("/token").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("grant_type", "authorization_code").param("code", code)
                .param("redirect_uri", REDIRECT_URI).param("client_id", CLIENT_ID).param("client_secret", CLIENT_SECRET))
            .andExpect(status().isOk()).andReturn();
        String refresh = JsonPath.read(token.getResponse().getContentAsString(), "$.refresh_token");

        // 登出（清 SSO 会话）
        mvc.perform(get("/logout").param("client_id", CLIENT_ID)
                .param("post_logout_redirect_uri", REDIRECT_URI).cookie(cookie))
            .andExpect(status().isFound());

        // refresh grant → 会话已失效 → invalid_grant（准 SLO）
        mvc.perform(post("/token").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("grant_type", "refresh_token").param("refresh_token", refresh)
                .param("client_id", CLIENT_ID).param("client_secret", CLIENT_SECRET))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("invalid_grant"));
    }
}
