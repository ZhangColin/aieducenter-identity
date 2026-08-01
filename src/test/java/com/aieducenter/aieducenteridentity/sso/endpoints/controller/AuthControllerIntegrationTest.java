package com.aieducenter.aieducenteridentity.sso.endpoints.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import com.aieducenter.aieducenteridentity.account.domain.aggregate.Account;
import com.aieducenter.aieducenteridentity.sso.endpoints.SsoIntegrationTestBase;
import com.jayway.jsonpath.JsonPath;

/**
 * {@code POST /api/auth/login} HTTP 黑盒集成测试（issue #15 AC）。
 *
 * <p>覆盖：登录成功（Set-Cookie + 302 code&state）、错密码 401、未知账号同 401（防枚举）、停用账号 401。</p>
 */
@Transactional
class AuthControllerIntegrationTest extends SsoIntegrationTestBase {

    private static final String PASSWORD = "Password123";

    private String loginBody(String account, String password) {
        return "{\"clientId\":\"" + CLIENT_ID + "\",\"redirectUri\":\"" + REDIRECT_URI + "\","
            + "\"state\":\"st\",\"nonce\":\"non\","
            + "\"account\":\"" + account + "\",\"password\":\"" + password + "\"}";
    }

    @Test
    void given_correct_password_when_login_then_set_cookie_and_redirect_with_code() throws Exception {
        String phone = "13900111001";
        createPhoneAccount(phone, PASSWORD);

        MvcResult result = mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody(phone, PASSWORD)))
            .andExpect(status().isFound())
            .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith(REDIRECT_URI + "?")))
            .andReturn();

        // 种 SSO cookie（httpOnly + Secure + SameSite=Lax）
        String setCookie = result.getResponse().getHeader("Set-Cookie");
        assertThat(setCookie).isNotNull();
        assertThat(setCookie).startsWith(ssoProperties.getCookieName() + "=");
        assertThat(setCookie).contains("HttpOnly");
        assertThat(setCookie).contains("SameSite=Lax");

        String location = result.getResponse().getHeader("Location");
        assertThat(queryParam(location, "code")).isNotBlank();
        assertThat(queryParam(location, "state")).isEqualTo("st");
    }

    @Test
    void given_wrong_password_and_unknown_account_when_login_then_same_401_anti_enumeration() throws Exception {
        String phone = "13900111002";
        createPhoneAccount(phone, PASSWORD);

        MvcResult wrong = mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody(phone, PASSWORD + "x")))
            .andExpect(status().isUnauthorized())
            .andReturn();

        MvcResult unknown = mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody("13999999999", PASSWORD)))
            .andExpect(status().isUnauthorized())
            .andReturn();

        // 防枚举：code + message 完全一致
        Integer wrongCode = JsonPath.read(wrong.getResponse().getContentAsString(), "$.code");
        Integer unknownCode = JsonPath.read(unknown.getResponse().getContentAsString(), "$.code");
        assertThat(wrongCode).isEqualTo(unknownCode);
        String wrongMsg = JsonPath.read(wrong.getResponse().getContentAsString(), "$.message");
        String unknownMsg = JsonPath.read(unknown.getResponse().getContentAsString(), "$.message");
        assertThat(wrongMsg).isEqualTo(unknownMsg);
    }

    @Test
    void given_disabled_account_when_login_then_401() throws Exception {
        String phone = "13900111003";
        Long userId = createPhoneAccount(phone, PASSWORD);
        Account account = accountRepository.findById(userId).orElseThrow();
        account.disable();
        accountRepository.save(account);

        mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody(phone, PASSWORD)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").exists());
    }
}
