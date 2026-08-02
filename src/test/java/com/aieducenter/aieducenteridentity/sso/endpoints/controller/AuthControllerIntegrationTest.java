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
 * {@code POST /api/auth/login} HTTP 黑盒集成测试（issue #15、#26 AC）。
 *
 * <p>覆盖：JSON 登录成功（Set-Cookie + 200 {redirectUrl} 带 code&state）、错密码 401、
 * 未知账号同 401（防枚举）、停用账号 401；form 变体成功保持 302 + Location。</p>
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

        // JSON 变体成功 = 200 + {redirectUrl}（issue #26，SPA fetch 读体后自行顶层导航；不带 Location）
        MvcResult result = mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody(phone, PASSWORD)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.redirectUrl", org.hamcrest.Matchers.startsWith(REDIRECT_URI + "?")))
            .andReturn();

        // 种 SSO cookie（httpOnly + Secure + SameSite=Lax）
        String setCookie = result.getResponse().getHeader("Set-Cookie");
        assertThat(setCookie).isNotNull();
        assertThat(setCookie).startsWith(ssoProperties.getCookieName() + "=");
        assertThat(setCookie).contains("HttpOnly");
        assertThat(setCookie).contains("SameSite=Lax");

        assertThat(result.getResponse().getHeader("Location")).isNull();
        String url = redirectUrl(result);
        assertThat(queryParam(url, "code")).isNotBlank();
        assertThat(queryParam(url, "state")).isEqualTo("st");
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

    @Test
    void given_correct_password_when_login_via_form_then_same_contract_as_json() throws Exception {
        String phone = "13900111004";
        createPhoneAccount(phone, PASSWORD);

        // identity-web 原生 form 顶层提交（issue #23）——form 变体成功保持 302 + cookie + code&state
        // （JSON 变体成功为 200 {redirectUrl}，issue #26）
        MvcResult result = mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("clientId", CLIENT_ID)
                .param("redirectUri", REDIRECT_URI)
                .param("state", "st")
                .param("nonce", "non")
                .param("account", phone)
                .param("password", PASSWORD))
            .andExpect(status().isFound())
            .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith(REDIRECT_URI + "?")))
            .andReturn();

        String setCookie = result.getResponse().getHeader("Set-Cookie");
        assertThat(setCookie).isNotNull();
        assertThat(setCookie).startsWith(ssoProperties.getCookieName() + "=");
        assertThat(queryParam(result, "code")).isNotBlank();
        assertThat(queryParam(result, "state")).isEqualTo("st");
    }

    @Test
    void given_wrong_password_when_login_via_form_then_401() throws Exception {
        String phone = "13900111005";
        createPhoneAccount(phone, PASSWORD);

        mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("clientId", CLIENT_ID)
                .param("redirectUri", REDIRECT_URI)
                .param("account", phone)
                .param("password", PASSWORD + "x"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").exists());
    }

    @Test
    void given_invalid_client_when_login_via_form_then_error_without_redirect() throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("clientId", "ghost")
                .param("redirectUri", REDIRECT_URI)
                .param("account", "13900111006")
                .param("password", PASSWORD))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("unauthorized_client"))
            .andReturn();
        assertThat(result.getResponse().getHeader("Location")).isNull();
    }
}
