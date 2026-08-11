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
import com.nimbusds.jwt.JWTClaimsSet;

/**
 * {@code POST /api/sso/login-code} HTTP 黑盒集成测试（issue #22、#26 AC）。
 *
 * <p>覆盖：验证码登录全链路（发 LOGIN 码 → 验码 → Set-Cookie + JSON 200 {redirectUrl} 带 code&state →
 * /token 换 token）、无密码账号可登、错码与「账号不存在 + 正确码」同一响应（防枚举）、停用账号 401、
 * 手机通道 + form 提交（成功保持 302 + Location）、client/redirect_uri 无效不重定向。</p>
 */
@Transactional
class LoginCodeControllerIntegrationTest extends SsoIntegrationTestBase {

    private static final String EMAIL = "coder@aieducenter.com";
    private static final String PHONE = "13900160001";

    private String loginCodeBody(String account, String code) {
        return "{\"clientId\":\"" + CLIENT_ID + "\",\"redirectUri\":\"" + REDIRECT_URI + "\","
            + "\"state\":\"st\",\"nonce\":\"non\",\"account\":\"" + account + "\",\"code\":\"" + code + "\"}";
    }

    @Test
    void given_valid_email_code_when_login_code_then_cookie_and_redirect_with_code() throws Exception {
        createEmailAccount(EMAIL, "Password123");
        String code = sendEmailCode(EMAIL, "LOGIN");

        // JSON 变体成功 = 200 + {redirectUrl}（issue #26；不带 Location）
        MvcResult result = mvc.perform(post("/api/sso/login-code")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginCodeBody(EMAIL, code)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.redirectUrl", org.hamcrest.Matchers.startsWith(REDIRECT_URI + "?")))
            .andReturn();

        String setCookie = result.getResponse().getHeader("Set-Cookie");
        assertThat(setCookie).isNotNull();
        assertThat(setCookie).startsWith(ssoProperties.getCookieName() + "=");
        assertThat(setCookie).contains("HttpOnly");
        assertThat(setCookie).contains("SameSite=Lax");
        assertThat(result.getResponse().getHeader("Location")).isNull();
        String url = redirectUrl(result);
        assertThat(queryParam(url, "code")).isNotBlank();
        assertThat(queryParam(url, "state")).isEqualTo("st");
        assertThat(accountRepository.findByEmail(EMAIL).orElseThrow().getLastLoginAt()).isNotNull();
    }

    @Test
    void given_passwordless_account_when_login_code_then_success() throws Exception {
        // 无密码账号（注册时密码可选，issue #22）——只能走验证码登录
        accountRepository.save(Account.register(EMAIL, null, null));
        String code = sendEmailCode(EMAIL, "LOGIN");

        mvc.perform(post("/api/sso/login-code")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginCodeBody(EMAIL, code)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.redirectUrl", org.hamcrest.Matchers.startsWith(REDIRECT_URI + "?")));
    }

    @Test
    void given_valid_code_when_exchange_token_then_token_issued_with_scope() throws Exception {
        createEmailAccount(EMAIL, "Password123");
        String code = sendEmailCode(EMAIL, "LOGIN");

        MvcResult login = mvc.perform(post("/api/sso/login-code")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"clientId\":\"" + CLIENT_ID + "\",\"redirectUri\":\"" + REDIRECT_URI + "\","
                    + "\"state\":\"st\",\"scope\":\"openid email\",\"account\":\"" + EMAIL + "\",\"code\":\"" + code + "\"}"))
            .andExpect(status().isOk())
            .andReturn();

        // login-code 发出的 code 能在 /token 正常换 token（与 login/register 同一发码契约）
        MvcResult token = mvc.perform(post("/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("grant_type", "authorization_code")
                .param("code", queryParam(redirectUrl(login), "code"))
                .param("redirect_uri", REDIRECT_URI)
                .param("client_id", CLIENT_ID)
                .param("client_secret", CLIENT_SECRET))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.access_token").isNotEmpty())
            .andExpect(jsonPath("$.id_token").isNotEmpty())
            .andExpect(jsonPath("$.refresh_token").isNotEmpty())
            .andReturn();

        JWTClaimsSet access = verifyJwtWithPublicKey(
            JsonPath.read(token.getResponse().getContentAsString(), "$.access_token"));
        assertThat(access.getStringClaim("scope")).isEqualTo("openid email");
    }

    /** 防枚举 AC 核心：错码（账号存在）与「账号不存在 + 正确码」同一响应（status/code/message 一致）。 */
    @Test
    void given_wrong_code_and_unknown_account_when_login_code_then_same_400_anti_enumeration() throws Exception {
        createEmailAccount(EMAIL, "Password123");

        // 账号存在 + 错码（未发码，任何码都是错码）
        MvcResult wrong = mvc.perform(post("/api/sso/login-code")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginCodeBody(EMAIL, "000000")))
            .andExpect(status().isBadRequest())
            .andReturn();

        // 账号不存在 + 正确码（LOGIN 码可对任意联络方式下发）
        String ghostCode = sendEmailCode("ghost@aieducenter.com", "LOGIN");
        MvcResult unknown = mvc.perform(post("/api/sso/login-code")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginCodeBody("ghost@aieducenter.com", ghostCode)))
            .andExpect(status().isBadRequest())
            .andReturn();

        Integer wrongCode = JsonPath.read(wrong.getResponse().getContentAsString(), "$.code");
        Integer unknownCode = JsonPath.read(unknown.getResponse().getContentAsString(), "$.code");
        assertThat(wrongCode).isEqualTo(unknownCode);
        String wrongMsg = JsonPath.read(wrong.getResponse().getContentAsString(), "$.message");
        String unknownMsg = JsonPath.read(unknown.getResponse().getContentAsString(), "$.message");
        assertThat(wrongMsg).isEqualTo(unknownMsg);
    }

    @Test
    void given_disabled_account_when_login_code_then_401() throws Exception {
        Long userId = createEmailAccount(EMAIL, "Password123");
        Account account = accountRepository.findById(userId).orElseThrow();
        account.disable();
        accountRepository.save(account);
        String code = sendEmailCode(EMAIL, "LOGIN");

        mvc.perform(post("/api/sso/login-code")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginCodeBody(EMAIL, code)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").exists());
    }

    @Test
    void given_valid_sms_code_when_login_code_via_form_then_success() throws Exception {
        createPhoneAccount(PHONE, "Password123");
        String code = sendSmsCode(PHONE, "LOGIN");

        // identity-web 原生 form 顶层提交（issue #23）——form 变体成功保持 302（JSON 变体为 200 {redirectUrl}，#26）
        MvcResult result = mvc.perform(post("/api/sso/login-code")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("clientId", CLIENT_ID)
                .param("redirectUri", REDIRECT_URI)
                .param("state", "st")
                .param("account", PHONE)
                .param("code", code))
            .andExpect(status().isFound())
            .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith(REDIRECT_URI + "?")))
            .andReturn();

        assertThat(result.getResponse().getHeader("Set-Cookie"))
            .startsWith(ssoProperties.getCookieName() + "=");
        assertThat(queryParam(result, "code")).isNotBlank();
    }

    @Test
    void given_invalid_client_when_login_code_then_error_without_redirect() throws Exception {
        MvcResult result = mvc.perform(post("/api/sso/login-code")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"clientId\":\"ghost\",\"redirectUri\":\"" + REDIRECT_URI + "\","
                    + "\"account\":\"" + EMAIL + "\",\"code\":\"123456\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("unauthorized_client"))
            .andReturn();
        assertThat(result.getResponse().getHeader("Location")).isNull();
    }
}
