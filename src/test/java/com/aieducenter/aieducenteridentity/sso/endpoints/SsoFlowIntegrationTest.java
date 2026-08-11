package com.aieducenter.aieducenteridentity.sso.endpoints;

import static org.assertj.core.api.Assertions.assertThat;
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

import com.aieducenter.aieducenteridentity.account.domain.aggregate.Account;
import com.aieducenter.aieducenteridentity.account.domain.repository.AccountRepository;
import com.aieducenter.aieducenteridentity.account.domain.service.AccountPasswordEncoderService;
import com.jayway.jsonpath.JsonPath;
import com.nimbusds.jwt.JWTClaimsSet;

import jakarta.servlet.http.Cookie;

/**
 * SSO 首次密码登录闭环端到端测试（issue #15 AC 总验收）。
 *
 * <p>一条完整链路：/authorize（无会话→登录页透传）→ /api/sso/login（建会话+种 cookie+发 code）→
 * /token（code 换 access/id/refresh，id 回带 nonce、公钥验签）→ 二次 /authorize（凭 cookie 免登直发 code）→
 * /api/sso/me（凭 cookie）。外加 code 一次性、me 无 cookie 401。</p>
 *
 * <p>code 60s 过期由 {@code RedisAuthorizationCodeStoreAdapterTest} 覆盖（避免真等 60s）。</p>
 */
@Transactional
class SsoFlowIntegrationTest extends SsoIntegrationTestBase {

    private static final String PHONE = "13900130001";
    private static final String PASSWORD = "Password123";
    private static final String STATE = "state-e2e";
    private static final String NONCE = "nonce-e2e";

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private AccountPasswordEncoderService passwordEncoderService;

    private Long createAccount() {
        Account account = Account.register(null, PHONE, passwordEncoderService.encodePassword(PASSWORD));
        return accountRepository.save(account).getId();
    }

    private String loginBody() {
        return "{\"clientId\":\"" + CLIENT_ID + "\",\"redirectUri\":\"" + REDIRECT_URI + "\","
            + "\"state\":\"" + STATE + "\",\"nonce\":\"" + NONCE + "\","
            + "\"account\":\"" + PHONE + "\",\"password\":\"" + PASSWORD + "\"}";
    }

    @Test
    void full_first_login_then_sso_then_token() throws Exception {
        Long userId = createAccount();
        Cookie[] ssoCookieHolder = new Cookie[1];

        // 1. /authorize 无 SSO cookie → 302 登录页透传 client_id/redirect_uri/state/nonce
        MvcResult auth1 = mvc.perform(get("/authorize")
                .param("client_id", CLIENT_ID)
                .param("redirect_uri", REDIRECT_URI)
                .param("state", STATE)
                .param("nonce", NONCE))
            .andExpect(status().isFound())
            .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith(ssoProperties.getLoginPageUrl())))
            .andReturn();
        assertThat(queryParam(auth1, "client_id")).isEqualTo(CLIENT_ID);
        assertThat(queryParam(auth1, "redirect_uri")).isEqualTo(REDIRECT_URI);
        assertThat(queryParam(auth1, "state")).isEqualTo(STATE);
        assertThat(queryParam(auth1, "nonce")).isEqualTo(NONCE);

        // 2. /api/sso/login 密码登录（JSON）→ Set-Cookie(SSO) + 200 {redirectUrl: redirect_uri?code&state}（#26）
        MvcResult login = mvc.perform(post("/api/sso/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody()))
            .andExpect(status().isOk())
            .andExpect(header().exists("Set-Cookie"))
            .andExpect(jsonPath("$.redirectUrl", org.hamcrest.Matchers.startsWith(REDIRECT_URI + "?")))
            .andReturn();
        String sessionId = extractCookieValue(login, ssoProperties.getCookieName());
        ssoCookieHolder[0] = new Cookie(ssoProperties.getCookieName(), sessionId);
        String code1 = queryParam(redirectUrl(login), "code");
        assertThat(code1).isNotBlank();
        assertThat(queryParam(redirectUrl(login), "state")).isEqualTo(STATE);

        // 3. /token code grant → access/id/refresh；id_token 含 nonce + 公钥验签通过
        MvcResult token = mvc.perform(post("/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("grant_type", "authorization_code")
                .param("code", code1)
                .param("redirect_uri", REDIRECT_URI)
                .param("client_id", CLIENT_ID)
                .param("client_secret", CLIENT_SECRET))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.access_token").isNotEmpty())
            .andExpect(jsonPath("$.id_token").isNotEmpty())
            .andExpect(jsonPath("$.refresh_token").isNotEmpty())
            .andReturn();
        JWTClaimsSet idClaims = verifyJwtWithPublicKey(JsonPath.read(token.getResponse().getContentAsString(), "$.id_token"));
        assertThat(idClaims.getStringClaim("nonce")).isEqualTo(NONCE);
        assertThat(idClaims.getSubject()).isEqualTo(String.valueOf(userId));

        // 4. 二次 /authorize 凭 SSO cookie 免登直发 code（不同于首次 code）
        MvcResult auth2 = mvc.perform(get("/authorize")
                .param("client_id", CLIENT_ID)
                .param("redirect_uri", REDIRECT_URI)
                .param("state", STATE)
                .param("nonce", NONCE)
                .cookie(ssoCookieHolder[0]))
            .andExpect(status().isFound())
            .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith(REDIRECT_URI + "?")))
            .andReturn();
        assertThat(queryParam(auth2, "code")).isNotEqualTo(code1);

        // 5. /api/sso/me 凭 SSO cookie → 200，返回当前用户
        mvc.perform(get("/api/sso/me").cookie(ssoCookieHolder[0]))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.phone").value(PHONE));
    }

    /**
     * scope 端到端一致性（issue #25 AC）：/authorize 跳登录页透传 scope；首次登录（login 带 scope）与
     * 二次免登同参时，code 换出的 access_token scope / id_token 声明一致。
     */
    @Test
    void given_scope_when_first_login_then_token_claims_match_second_sso() throws Exception {
        String email = "scope-e2e@aieducenter.com";
        accountRepository.save(Account.register(email, PHONE, passwordEncoderService.encodePassword(PASSWORD)));
        String scope = "openid profile email phone";

        // 1. /authorize 带 scope 无会话 → 302 登录页透传 scope
        MvcResult auth1 = mvc.perform(get("/authorize")
                .param("client_id", CLIENT_ID)
                .param("redirect_uri", REDIRECT_URI)
                .param("state", STATE)
                .param("nonce", NONCE)
                .param("scope", scope))
            .andExpect(status().isFound())
            .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith(ssoProperties.getLoginPageUrl())))
            .andReturn();
        assertThat(queryParam(auth1, "scope")).isEqualTo(scope);

        // 2. 首次登录带 scope（JSON 成功 200 {redirectUrl}，#26）→ code 换 token：access_token 绑请求 scope、id_token 出 email/phone 声明
        MvcResult login = mvc.perform(post("/api/sso/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"clientId\":\"" + CLIENT_ID + "\",\"redirectUri\":\"" + REDIRECT_URI + "\","
                    + "\"state\":\"" + STATE + "\",\"nonce\":\"" + NONCE + "\",\"scope\":\"" + scope + "\","
                    + "\"account\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
            .andExpect(status().isOk())
            .andReturn();
        Cookie ssoCookie = new Cookie(ssoProperties.getCookieName(),
            extractCookieValue(login, ssoProperties.getCookieName()));

        MvcResult token1 = exchangeCode(queryParam(redirectUrl(login), "code"));
        JWTClaimsSet access1 = verifyJwtWithPublicKey(JsonPath.read(token1.getResponse().getContentAsString(), "$.access_token"));
        JWTClaimsSet id1 = verifyJwtWithPublicKey(JsonPath.read(token1.getResponse().getContentAsString(), "$.id_token"));
        assertThat(access1.getStringClaim("scope")).isEqualTo(scope);
        assertThat(id1.getStringClaim("email")).isEqualTo(email);
        assertThat(id1.getStringClaim("phone_number")).isEqualTo(PHONE);

        // 3. 二次 /authorize 同参凭 cookie 免登 → code 换 token：scope/声明与首次登录一致
        MvcResult auth2 = mvc.perform(get("/authorize")
                .param("client_id", CLIENT_ID)
                .param("redirect_uri", REDIRECT_URI)
                .param("state", STATE)
                .param("nonce", NONCE)
                .param("scope", scope)
                .cookie(ssoCookie))
            .andExpect(status().isFound())
            .andReturn();

        MvcResult token2 = exchangeCode(queryParam(auth2, "code"));
        JWTClaimsSet access2 = verifyJwtWithPublicKey(JsonPath.read(token2.getResponse().getContentAsString(), "$.access_token"));
        JWTClaimsSet id2 = verifyJwtWithPublicKey(JsonPath.read(token2.getResponse().getContentAsString(), "$.id_token"));
        assertThat(access2.getStringClaim("scope")).isEqualTo(access1.getStringClaim("scope"));
        assertThat(id2.getStringClaim("email")).isEqualTo(id1.getStringClaim("email"));
        assertThat(id2.getStringClaim("phone_number")).isEqualTo(id1.getStringClaim("phone_number"));
    }

    /** code 换 token（授权码 grant），返回 /token 响应。 */
    private MvcResult exchangeCode(String code) throws Exception {
        return mvc.perform(post("/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("grant_type", "authorization_code")
                .param("code", code)
                .param("redirect_uri", REDIRECT_URI)
                .param("client_id", CLIENT_ID)
                .param("client_secret", CLIENT_SECRET))
            .andExpect(status().isOk())
            .andReturn();
    }

    @Test
    void given_consumed_code_when_token_again_then_invalid_grant() throws Exception {
        createAccount();
        MvcResult login = mvc.perform(post("/api/sso/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody()))
            .andExpect(status().isOk()).andReturn();
        String code = queryParam(redirectUrl(login), "code");

        // 首次换 token 成功
        mvc.perform(post("/token").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("grant_type", "authorization_code").param("code", code)
                .param("redirect_uri", REDIRECT_URI)
                .param("client_id", CLIENT_ID).param("client_secret", CLIENT_SECRET))
            .andExpect(status().isOk());

        // 同一 code 再换 → 一次性，invalid_grant
        mvc.perform(post("/token").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("grant_type", "authorization_code").param("code", code)
                .param("redirect_uri", REDIRECT_URI)
                .param("client_id", CLIENT_ID).param("client_secret", CLIENT_SECRET))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("invalid_grant"));
    }

    @Test
    void given_no_sso_cookie_when_access_me_then_401() throws Exception {
        mvc.perform(get("/api/sso/me"))
            .andExpect(status().isUnauthorized());
    }
}
