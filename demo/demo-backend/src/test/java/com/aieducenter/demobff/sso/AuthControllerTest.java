package com.aieducenter.demobff.sso;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.aieducenter.demobff.config.SsoProperties;

import jakarta.servlet.http.Cookie;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock OidcClient oidcClient;
    @Mock BffSessionStore sessionStore;
    @InjectMocks AuthController controller;

    private SsoProperties props;
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        props = new SsoProperties();
        props.setIssuer("http://idp");
        props.setRedirectUri("http://demo.localhost:3000/auth/callback");
        props.setAppBaseUrl("http://demo.localhost:3000");
        controller.props = props;  // AuthController 包级可见字段，便于测试注入
        mvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void login_sets_txn_cookie_and_redirects_to_authorize() throws Exception {
        when(oidcClient.authorizeUrl(any(), any())).thenReturn("http://idp/authorize?state=st");
        mvc.perform(get("/auth/login"))
            .andExpect(status().isFound())
            .andExpect(header().string("Location", "http://idp/authorize?state=st"))
            .andExpect(cookie().exists("oauth_txn"));
    }

    @Test
    void login_when_cookie_secure_disabled_then_txn_cookie_not_secure() throws Exception {
        // issue #37：cookieSecure flag 必须透传到 oauth_txn cookie——local 跑 http://*.localhost，
        // Safari/Firefox 不豁免 Secure-over-http（仅 Chrome/Edge 豁免），Secure cookie 被拒存 →
        // oauth_txn 落不了地 → callback 恒 state_mismatch。故关 Secure 后 oauth_txn 必须非 Secure。
        props.setCookieSecure(false);
        when(oidcClient.authorizeUrl(any(), any())).thenReturn("http://idp/authorize?state=st");
        mvc.perform(get("/auth/login"))
            .andExpect(cookie().secure("oauth_txn", false));
    }

    @Test
    void login_when_cookie_secure_default_then_txn_cookie_secure() throws Exception {
        // prod 默认 true（https）——守护默认值不被改坏。
        when(oidcClient.authorizeUrl(any(), any())).thenReturn("http://idp/authorize?state=st");
        mvc.perform(get("/auth/login"))
            .andExpect(cookie().secure("oauth_txn", true));
    }

    @Test
    void callback_exchanges_code_and_sets_session_cookie() throws Exception {
        when(oidcClient.exchangeCode("c1")).thenReturn(
            new TokenResponse("a", "Bearer", 900L, "r", "IDT"));
        // 注意：callback 不解 id_token（token 原样存服务端会话），故不 stub decodeIdToken。

        String txn = "st:nc";
        MvcResult result = mvc.perform(get("/auth/callback")
                .param("code", "c1").param("state", "st")
                .cookie(new Cookie("oauth_txn", txn)))
            .andExpect(status().isFound())
            .andExpect(header().string("Location", "http://demo.localhost:3000/"))
            .andExpect(cookie().exists("demo_session"))
            .andReturn();
        assertThat(result.getResponse().getCookie("oauth_txn").getMaxAge()).isZero();
    }

    @Test
    void callback_when_cookie_secure_disabled_then_session_cookie_not_secure() throws Exception {
        // issue #37：demo_session 同样要走 cookieSecure flag——local 关 Secure 才种得上会话 cookie，
        // 否则即便换 token 成功，Safari/Firefox 下 demo_session 仍落不了地、登录态丢失。
        props.setCookieSecure(false);
        when(oidcClient.exchangeCode("c1")).thenReturn(
            new TokenResponse("a", "Bearer", 900L, "r", "IDT"));
        mvc.perform(get("/auth/callback")
                .param("code", "c1").param("state", "st")
                .cookie(new Cookie("oauth_txn", "st:nc")))
            .andExpect(status().isFound())
            .andExpect(cookie().secure("demo_session", false));
    }

    @Test
    void callback_state_mismatch_redirects_with_error() throws Exception {
        mvc.perform(get("/auth/callback")
                .param("code", "c1").param("state", "evil")
                .cookie(new Cookie("oauth_txn", "st:nc")))
            .andExpect(status().isFound())
            .andExpect(header().string("Location",
                org.hamcrest.Matchers.containsString("error=state_mismatch")));
    }

    @Test
    void callback_exchange_failure_redirects_with_error() throws Exception {
        // issue #35：OidcClient 现把 /token 失败包成 TokenExchangeException（带状态码 + 协议错误体）。
        when(oidcClient.exchangeCode("bad")).thenThrow(
            new TokenExchangeException("identity /token 返回 401", 401,
                "{\"error\":\"invalid_client\",\"error_description\":\"client 认证失败\"}",
                new RuntimeException("cause")));

        MvcResult result = mvc.perform(get("/auth/callback")
                .param("code", "bad").param("state", "st")
                .cookie(new Cookie("oauth_txn", "st:nc")))
            .andExpect(status().isFound())
            .andExpect(header().string("Location",
                org.hamcrest.Matchers.containsString("error=exchange_failed")))
            .andReturn();
        // 失败也要清 oauth_txn
        assertThat(result.getResponse().getCookie("oauth_txn").getMaxAge()).isZero();
    }

    @Test
    void callback_unexpected_failure_still_redirects_safely() throws Exception {
        // 兜底分支：未预期的异常也不冒泡成 500，仍安全回 exchange_failed。
        when(oidcClient.exchangeCode("bad")).thenThrow(new RuntimeException("unexpected"));

        MvcResult result = mvc.perform(get("/auth/callback")
                .param("code", "bad").param("state", "st")
                .cookie(new Cookie("oauth_txn", "st:nc")))
            .andExpect(status().isFound())
            .andExpect(header().string("Location",
                org.hamcrest.Matchers.containsString("error=exchange_failed")))
            .andReturn();
        assertThat(result.getResponse().getCookie("oauth_txn").getMaxAge()).isZero();
    }

    @Test
    void logout_clears_local_session_and_cookie() throws Exception {
        // issue #38：登出仍先清本地 demo 会话（BffSessionStore + demo_session cookie），再跳 identity。
        when(oidcClient.logoutUrl(any(), any())).thenReturn("http://idp/logout?client_id=demo-client");
        MvcResult result = mvc.perform(post("/auth/logout").cookie(new Cookie("demo_session", "sid")))
            .andExpect(status().isFound())
            .andExpect(cookie().exists("demo_session"))
            .andReturn();
        assertThat(result.getResponse().getCookie("demo_session").getMaxAge()).isZero();
        verify(sessionStore).remove("sid");
    }

    @Test
    void logout_redirects_to_identity_rp_initiated_logout() throws Exception {
        // issue #38：清完本地会话后 302 到 identity /logout——让浏览器顶层导航到 identity 清 SSO 会话 + sso_session cookie，
        // 否则 identity 侧 sso_session 仍在 → 下次 /authorize 直接发 code（二次免登）。
        // post_logout_redirect_uri = demo 首页（appBaseUrl/，需在 app-registry 白名单），state 一次性随机串。
        when(oidcClient.logoutUrl(any(), any())).thenReturn(
            "http://idp/logout?client_id=demo-client&post_logout_redirect_uri=http%3A%2F%2Fdemo.localhost%3A3000%2F&state=st");
        mvc.perform(post("/auth/logout"))
            .andExpect(status().isFound())
            .andExpect(header().string("Location",
                "http://idp/logout?client_id=demo-client&post_logout_redirect_uri=http%3A%2F%2Fdemo.localhost%3A3000%2F&state=st"));
        verify(oidcClient).logoutUrl(eq("http://demo.localhost:3000/"), anyString());
    }
}
