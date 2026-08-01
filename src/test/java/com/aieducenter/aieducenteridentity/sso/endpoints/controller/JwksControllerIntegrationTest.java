package com.aieducenter.aieducenteridentity.sso.endpoints.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;

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
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.SignedJWT;

/**
 * {@code GET /jwks} HTTP 黑盒集成测试（issue #17 AC）。
 *
 * <p>覆盖：返回 RS256 公钥集（kty/use/alg/kid/n/e）；kid 与 access token 头 kid 对应；
 * 用 n/e 重构的公钥能本地验真实 access token 签名。</p>
 */
@Transactional
class JwksControllerIntegrationTest extends SsoIntegrationTestBase {

    private static final String PASSWORD = "Password123";

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private AccountPasswordEncoderService passwordEncoderService;

    @Test
    void get_jwks_then_rs256_public_key_set_verifies_real_access_token() throws Exception {
        String accessToken = obtainAccessToken();

        MvcResult result = mvc.perform(get("/jwks"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.keys[0].kty").value("RSA"))
            .andExpect(jsonPath("$.keys[0].use").value("sig"))
            .andExpect(jsonPath("$.keys[0].alg").value("RS256"))
            .andExpect(jsonPath("$.keys[0].kid").isNotEmpty())
            .andExpect(jsonPath("$.keys[0].n").isNotEmpty())
            .andExpect(jsonPath("$.keys[0].e").value("AQAB"))
            .andReturn();
        String body = result.getResponse().getContentAsString();

        // kid 与 access token 头 kid 一致（同一签名密钥）
        String kid = JsonPath.read(body, "$.keys[0].kid");
        assertThat(SignedJWT.parse(accessToken).getHeader().getKeyID()).isEqualTo(kid);

        // 用 n/e 重构公钥，本地验真实 access token 签名（对接方自发现 + 本地验签的核心能力）
        String n = JsonPath.read(body, "$.keys[0].n");
        String e = JsonPath.read(body, "$.keys[0].e");
        BigInteger modulus = new BigInteger(1, Base64.getUrlDecoder().decode(n));
        BigInteger exponent = new BigInteger(1, Base64.getUrlDecoder().decode(e));
        RSAPublicKey pub = (RSAPublicKey) KeyFactory.getInstance("RSA")
            .generatePublic(new RSAPublicKeySpec(modulus, exponent));

        boolean verified = SignedJWT.parse(accessToken).verify(new RSASSAVerifier(pub));
        assertThat(verified).as("用 /jwks 公钥本地验真实 access token 签名").isTrue();
    }

    /** 走完整 /authorize→/token 拿一个真实 access token。 */
    private String obtainAccessToken() throws Exception {
        Account account = Account.register(null, "13900112040",
            passwordEncoderService.encodePassword(PASSWORD));
        Long userId = accountRepository.save(account).getId();
        SsoSession session = createSsoSession(userId, "用户");
        MvcResult authorize = mvc.perform(get("/authorize")
                .param("client_id", CLIENT_ID)
                .param("redirect_uri", REDIRECT_URI)
                .param("state", "st")
                .param("nonce", "n")
                .param("scope", "openid")
                .cookie(ssoCookie(session.sessionId())))
            .andExpect(status().isFound())
            .andReturn();
        String code = queryParam(authorize.getResponse().getHeader("Location"), "code");
        MvcResult token = mvc.perform(post("/token").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("grant_type", "authorization_code")
                .param("code", code)
                .param("redirect_uri", REDIRECT_URI)
                .param("client_id", CLIENT_ID)
                .param("client_secret", CLIENT_SECRET))
            .andExpect(status().isOk())
            .andReturn();
        return JsonPath.read(token.getResponse().getContentAsString(), "$.access_token");
    }
}
