package com.aieducenter.aieducenteridentity.sso.endpoints.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import com.aieducenter.aieducenteridentity.sso.domain.session.SsoSession;
import com.aieducenter.aieducenteridentity.sso.endpoints.SsoIntegrationTestBase;
import com.jayway.jsonpath.JsonPath;
import com.nimbusds.jwt.JWTClaimsSet;

/**
 * {@code POST /token} HTTP 黑盒集成测试（issue #15 AC）。
 *
 * <p>覆盖：code grant 换 access/id/refresh（公钥验签 + id_token 回带 nonce）、refresh grant 轮换、
 * code 一次性、redirect_uri 校验、client_secret 错。</p>
 */
@Transactional
class TokenControllerIntegrationTest extends SsoIntegrationTestBase {

    private static final String NONCE = "nonce-abc";
    private static final String PASSWORD = "Password123";

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private AccountPasswordEncoderService passwordEncoderService;

    private Long createAccount(String phone) {
        Account account = Account.register(null, phone, passwordEncoderService.encodePassword(PASSWORD));
        return accountRepository.save(account).getId();
    }

    /** 走 /authorize 拿一个真实 code（建会话 + cookie + 二次免登发 code）。 */
    private String obtainCode(Long userId) throws Exception {
        return obtainCode(userId, null);
    }

    /** 走 /authorize 拿一个真实 code，可带 scope（透传进 code → access token）。 */
    private String obtainCode(Long userId, String scope) throws Exception {
        SsoSession session = createSsoSession(userId, "用户");
        MvcResult result = mvc.perform(get("/authorize")
                .param("client_id", CLIENT_ID)
                .param("redirect_uri", REDIRECT_URI)
                .param("state", "st")
                .param("nonce", NONCE)
                .param("scope", scope)
                .cookie(ssoCookie(session.sessionId())))
            .andExpect(status().isFound())
            .andReturn();
        return queryParam(result.getResponse().getHeader("Location"), "code");
    }

    private MvcResult exchangeCode(String code) throws Exception {
        return mvc.perform(post("/token").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("grant_type", "authorization_code")
                .param("code", code)
                .param("redirect_uri", REDIRECT_URI)
                .param("client_id", CLIENT_ID)
                .param("client_secret", CLIENT_SECRET))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.access_token").isNotEmpty())
            .andExpect(jsonPath("$.id_token").isNotEmpty())
            .andExpect(jsonPath("$.refresh_token").isNotEmpty())
            .andExpect(jsonPath("$.token_type").value("Bearer"))
            .andReturn();
    }

    @Test
    void given_code_grant_when_token_then_access_id_refresh_and_nonce_in_id_token() throws Exception {
        Long userId = createAccount("13900112001");
        String code = obtainCode(userId);
        MvcResult result = exchangeCode(code);
        String body = result.getResponse().getContentAsString();

        String idToken = JsonPath.read(body, "$.id_token");
        JWTClaimsSet idClaims = verifyJwtWithPublicKey(idToken);
        assertThat(idClaims.getStringClaim("nonce")).isEqualTo(NONCE);
        assertThat(idClaims.getSubject()).isEqualTo(String.valueOf(userId));
        // access JWT 亦可用公钥验签
        verifyJwtWithPublicKey(JsonPath.read(body, "$.access_token"));
    }

    @Test
    void given_code_grant_with_scope_when_token_then_access_token_carries_scope() throws Exception {
        Long userId = createAccount("13900112010");
        String code = obtainCode(userId, "openid profile email phone");

        MvcResult result = exchangeCode(code);
        String accessToken = JsonPath.read(result.getResponse().getContentAsString(), "$.access_token");

        // access JWT 带 scope claim（/userinfo 据此过滤返回资料，issue #17）
        JWTClaimsSet accessClaims = verifyJwtWithPublicKey(accessToken);
        assertThat(accessClaims.getStringClaim("scope")).isEqualTo("openid profile email phone");
    }

    @Test
    void given_code_reuse_when_token_then_invalid_grant() throws Exception {
        Long userId = createAccount("13900112002");
        String code = obtainCode(userId);
        exchangeCode(code); // 首次消费

        mvc.perform(post("/token").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("grant_type", "authorization_code")
                .param("code", code)
                .param("redirect_uri", REDIRECT_URI)
                .param("client_id", CLIENT_ID)
                .param("client_secret", CLIENT_SECRET))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("invalid_grant"));
    }

    @Test
    void given_redirect_uri_mismatch_when_token_then_invalid_grant() throws Exception {
        Long userId = createAccount("13900112003");
        String code = obtainCode(userId);

        mvc.perform(post("/token").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("grant_type", "authorization_code")
                .param("code", code)
                .param("redirect_uri", "https://evil.example/callback")
                .param("client_id", CLIENT_ID)
                .param("client_secret", CLIENT_SECRET))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("invalid_grant"));
    }

    @Test
    void given_wrong_client_secret_when_token_then_invalid_client() throws Exception {
        Long userId = createAccount("13900112004");
        String code = obtainCode(userId);

        mvc.perform(post("/token").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("grant_type", "authorization_code")
                .param("code", code)
                .param("redirect_uri", REDIRECT_URI)
                .param("client_id", CLIENT_ID)
                .param("client_secret", "wrong-secret"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error").value("invalid_client"));
    }

    @Test
    void given_refresh_grant_when_token_then_rotated_and_old_invalid() throws Exception {
        Long userId = createAccount("13900112005");
        String code = obtainCode(userId);
        String refresh = JsonPath.read(exchangeCode(code).getResponse().getContentAsString(), "$.refresh_token");

        // refresh grant → 新三件套，refresh 轮换
        MvcResult refreshed = mvc.perform(post("/token").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("grant_type", "refresh_token")
                .param("refresh_token", refresh)
                .param("client_id", CLIENT_ID)
                .param("client_secret", CLIENT_SECRET))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.access_token").isNotEmpty())
            .andReturn();
        String newRefresh = JsonPath.read(refreshed.getResponse().getContentAsString(), "$.refresh_token");
        assertThat(newRefresh).isNotEqualTo(refresh);

        // 旧 refresh 已轮换失效 → invalid_grant
        mvc.perform(post("/token").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("grant_type", "refresh_token")
                .param("refresh_token", refresh)
                .param("client_id", CLIENT_ID)
                .param("client_secret", CLIENT_SECRET))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("invalid_grant"));
    }
}
