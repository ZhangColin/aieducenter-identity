package com.aieducenter.aieducenteridentity.sso.endpoints.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
 * {@code POST /api/auth/register} HTTP 黑盒集成测试（issue #18 AC）。
 *
 * <p>覆盖：注册成功（建号 + Set-Cookie + 302 code&state）、注册即登录（code 能换 token、同凭据可再登录）、
 * email/phone 重复 409、联络方式缺失/格式错/缺密码 400、client/redirect_uri 无效不重定向。</p>
 */
@Transactional
class RegisterControllerIntegrationTest extends SsoIntegrationTestBase {

    private static final String EMAIL = "newbie@aieducenter.com";
    private static final String PHONE = "13900150001";
    private static final String PASSWORD = "Password123";

    private String registerBody(String email, String phone, String password) {
        StringBuilder body = new StringBuilder("{\"clientId\":\"" + CLIENT_ID + "\",\"redirectUri\":\"" + REDIRECT_URI + "\","
            + "\"state\":\"st\",\"nonce\":\"non\",");
        if (email != null) {
            body.append("\"email\":\"").append(email).append("\",");
        }
        if (phone != null) {
            body.append("\"phone\":\"").append(phone).append("\",");
        }
        if (password != null) {
            body.append("\"password\":\"").append(password).append("\",");
        }
        body.deleteCharAt(body.length() - 1).append("}");
        return body.toString();
    }

    @Test
    void given_new_email_when_register_then_account_created_and_redirect_with_code() throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerBody(EMAIL, null, PASSWORD)))
            .andExpect(status().isFound())
            .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith(REDIRECT_URI + "?")))
            .andReturn();

        // 建号：email 落库 + 密码 hash（非明文）+ 注册即登录（lastLoginAt 已记）
        Account account = accountRepository.findByEmail(EMAIL).orElseThrow();
        assertThat(account.getPasswordHash()).isNotBlank().isNotEqualTo(PASSWORD);
        assertThat(account.getLastLoginAt()).isNotNull();

        // 种 SSO cookie + 302 带 code&state
        String setCookie = result.getResponse().getHeader("Set-Cookie");
        assertThat(setCookie).isNotNull();
        assertThat(setCookie).startsWith(ssoProperties.getCookieName() + "=");
        assertThat(setCookie).contains("HttpOnly");
        assertThat(setCookie).contains("SameSite=Lax");
        assertThat(queryParam(result, "code")).isNotBlank();
        assertThat(queryParam(result, "state")).isEqualTo("st");
    }

    @Test
    void given_registered_when_exchange_code_and_login_again_then_both_work() throws Exception {
        // 注册即登录：发出的 code 能在 /token 正常换 token
        MvcResult register = mvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerBody(EMAIL, null, PASSWORD)))
            .andExpect(status().isFound())
            .andReturn();
        String code = queryParam(register, "code");

        mvc.perform(post("/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("grant_type", "authorization_code")
                .param("code", code)
                .param("redirect_uri", REDIRECT_URI)
                .param("client_id", CLIENT_ID)
                .param("client_secret", CLIENT_SECRET))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.access_token").isNotEmpty())
            .andExpect(jsonPath("$.id_token").isNotEmpty())
            .andExpect(jsonPath("$.refresh_token").isNotEmpty());

        // 注册后同凭据能走 /api/auth/login 登录
        mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"clientId\":\"" + CLIENT_ID + "\",\"redirectUri\":\"" + REDIRECT_URI + "\","
                    + "\"state\":\"st2\",\"account\":\"" + EMAIL + "\",\"password\":\"" + PASSWORD + "\"}"))
            .andExpect(status().isFound())
            .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith(REDIRECT_URI + "?")));
    }

    @Test
    void given_existing_email_when_register_then_409_and_no_second_account() throws Exception {
        createEmailAccount(EMAIL, PASSWORD);

        mvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerBody(EMAIL, null, PASSWORD)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").exists());

        assertThat(accountRepository.findByEmail(EMAIL)).isPresent();
        // 未建第二个号：email 唯一索引下仍然只能查到一个，且 phone 侧没有新账号
        assertThat(accountRepository.count()).isEqualTo(1);
    }

    @Test
    void given_existing_phone_when_register_then_409() throws Exception {
        createPhoneAccount(PHONE, PASSWORD);

        mvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerBody(null, PHONE, PASSWORD)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").exists());
    }

    @Test
    void given_neither_email_nor_phone_when_register_then_400() throws Exception {
        mvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerBody(null, null, PASSWORD)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").exists());
    }

    @Test
    void given_malformed_email_or_phone_when_register_then_400() throws Exception {
        mvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerBody("not-an-email", null, PASSWORD)))
            .andExpect(status().isBadRequest());

        mvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerBody(null, "12345", PASSWORD)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void given_missing_password_when_register_then_400() throws Exception {
        mvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerBody(EMAIL, null, null)))
            .andExpect(status().isBadRequest());

        assertThat(accountRepository.findByEmail(EMAIL)).isEmpty();
    }

    @Test
    void given_invalid_client_or_redirect_uri_when_register_then_error_without_redirect() throws Exception {
        // client_id 无效 → 400 unauthorized_client，不重定向
        MvcResult badClient = mvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"clientId\":\"ghost\",\"redirectUri\":\"" + REDIRECT_URI + "\","
                    + "\"email\":\"" + EMAIL + "\",\"password\":\"" + PASSWORD + "\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("unauthorized_client"))
            .andReturn();
        assertThat(badClient.getResponse().getHeader("Location")).isNull();

        // redirect_uri 未登记 → 400 invalid_request，不重定向
        MvcResult badRedirect = mvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"clientId\":\"" + CLIENT_ID + "\",\"redirectUri\":\"https://evil.example/callback\","
                    + "\"email\":\"" + EMAIL + "\",\"password\":\"" + PASSWORD + "\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("invalid_request"))
            .andReturn();
        assertThat(badRedirect.getResponse().getHeader("Location")).isNull();

        assertThat(accountRepository.findByEmail(EMAIL)).isEmpty();
    }

    @Test
    void given_new_phone_when_register_via_form_then_same_contract_as_json() throws Exception {
        // identity-web 原生 form 顶层提交（issue #23）——与 JSON 同一契约
        MvcResult result = mvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("clientId", CLIENT_ID)
                .param("redirectUri", REDIRECT_URI)
                .param("state", "st")
                .param("phone", PHONE)
                .param("password", PASSWORD))
            .andExpect(status().isFound())
            .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith(REDIRECT_URI + "?")))
            .andReturn();

        assertThat(result.getResponse().getHeader("Set-Cookie"))
            .startsWith(ssoProperties.getCookieName() + "=");
        assertThat(queryParam(result, "code")).isNotBlank();
        assertThat(accountRepository.findByPhone(PHONE)).isPresent();
    }

    /**
     * 注册带 scope（issue #25 AC）：form 入口接受 scope 并透传发码；注册即登录与二次免登同参时，
     * code 换出的 access_token scope / id_token 声明一致（与 login 侧同一发码契约）。
     */
    @Test
    void given_scope_when_register_then_token_claims_match_second_sso() throws Exception {
        String scope = "openid profile phone";
        MvcResult register = mvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("clientId", CLIENT_ID)
                .param("redirectUri", REDIRECT_URI)
                .param("state", "st")
                .param("nonce", "non")
                .param("scope", scope)
                .param("phone", PHONE)
                .param("password", PASSWORD))
            .andExpect(status().isFound())
            .andReturn();
        jakarta.servlet.http.Cookie ssoCookie = new jakarta.servlet.http.Cookie(
            ssoProperties.getCookieName(), extractCookieValue(register, ssoProperties.getCookieName()));

        // 注册即登录发的 code：access_token 绑请求 scope、id_token 出 phone 声明
        MvcResult token1 = mvc.perform(post("/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("grant_type", "authorization_code")
                .param("code", queryParam(register, "code"))
                .param("redirect_uri", REDIRECT_URI)
                .param("client_id", CLIENT_ID)
                .param("client_secret", CLIENT_SECRET))
            .andExpect(status().isOk())
            .andReturn();
        JWTClaimsSet access1 = verifyJwtWithPublicKey(
            JsonPath.read(token1.getResponse().getContentAsString(), "$.access_token"));
        JWTClaimsSet id1 = verifyJwtWithPublicKey(
            JsonPath.read(token1.getResponse().getContentAsString(), "$.id_token"));
        assertThat(access1.getStringClaim("scope")).isEqualTo(scope);
        assertThat(id1.getStringClaim("phone_number")).isEqualTo(PHONE);

        // 二次 /authorize 同参凭 cookie 免登 → code 换 token：scope/声明与注册即登录一致
        MvcResult auth2 = mvc.perform(get("/authorize")
                .param("client_id", CLIENT_ID)
                .param("redirect_uri", REDIRECT_URI)
                .param("state", "st")
                .param("nonce", "non")
                .param("scope", scope)
                .cookie(ssoCookie))
            .andExpect(status().isFound())
            .andReturn();
        MvcResult token2 = mvc.perform(post("/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("grant_type", "authorization_code")
                .param("code", queryParam(auth2, "code"))
                .param("redirect_uri", REDIRECT_URI)
                .param("client_id", CLIENT_ID)
                .param("client_secret", CLIENT_SECRET))
            .andExpect(status().isOk())
            .andReturn();
        JWTClaimsSet access2 = verifyJwtWithPublicKey(
            JsonPath.read(token2.getResponse().getContentAsString(), "$.access_token"));
        JWTClaimsSet id2 = verifyJwtWithPublicKey(
            JsonPath.read(token2.getResponse().getContentAsString(), "$.id_token"));
        assertThat(access2.getStringClaim("scope")).isEqualTo(access1.getStringClaim("scope"));
        assertThat(id2.getStringClaim("phone_number")).isEqualTo(id1.getStringClaim("phone_number"));
    }

    @Test
    void given_existing_email_when_register_via_form_then_409() throws Exception {
        createEmailAccount(EMAIL, PASSWORD);

        mvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("clientId", CLIENT_ID)
                .param("redirectUri", REDIRECT_URI)
                .param("email", EMAIL)
                .param("password", PASSWORD))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").exists());
    }

    @Test
    void given_missing_password_when_register_via_form_then_400() throws Exception {
        mvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("clientId", CLIENT_ID)
                .param("redirectUri", REDIRECT_URI)
                .param("email", EMAIL))
            .andExpect(status().isBadRequest());

        assertThat(accountRepository.findByEmail(EMAIL)).isEmpty();
    }
}
