package com.aieducenter.demobff.sso;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
    void logout_clears_session_cookie() throws Exception {
        mvc.perform(post("/auth/logout").cookie(new Cookie("demo_session", "sid")))
            .andExpect(status().isFound())
            .andExpect(header().string("Location", "http://demo.localhost:3000/"))
            .andExpect(cookie().exists("demo_session"));
    }
}
