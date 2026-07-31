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
 * <p>一条完整链路：/authorize（无会话→登录页透传）→ /api/auth/login（建会话+种 cookie+发 code）→
 * /token（code 换 access/id/refresh，id 回带 nonce、公钥验签）→ 二次 /authorize（凭 cookie 免登直发 code）→
 * /api/account/me（凭 cookie）。外加 code 一次性、me 无 cookie 401。</p>
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

        // 2. /api/auth/login 密码登录 → Set-Cookie(SSO) + 302 redirect_uri?code&state
        MvcResult login = mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody()))
            .andExpect(status().isFound())
            .andExpect(header().exists("Set-Cookie"))
            .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith(REDIRECT_URI + "?")))
            .andReturn();
        String sessionId = extractCookieValue(login, ssoProperties.getCookieName());
        ssoCookieHolder[0] = new Cookie(ssoProperties.getCookieName(), sessionId);
        String code1 = queryParam(login, "code");
        assertThat(code1).isNotBlank();
        assertThat(queryParam(login, "state")).isEqualTo(STATE);

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

        // 5. /api/account/me 凭 SSO cookie → 200，返回当前用户
        mvc.perform(get("/api/account/me").cookie(ssoCookieHolder[0]))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.phone").value(PHONE));
    }

    @Test
    void given_consumed_code_when_token_again_then_invalid_grant() throws Exception {
        createAccount();
        MvcResult login = mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody()))
            .andExpect(status().isFound()).andReturn();
        String code = queryParam(login, "code");

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
        mvc.perform(get("/api/account/me"))
            .andExpect(status().isUnauthorized());
    }
}
