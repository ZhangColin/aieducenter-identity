package com.aieducenter.aieducenteridentity.sso.endpoints.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import com.aieducenter.aieducenteridentity.account.domain.aggregate.Account;
import com.aieducenter.aieducenteridentity.account.domain.aggregate.Profile;
import com.aieducenter.aieducenteridentity.account.domain.repository.AccountRepository;
import com.aieducenter.aieducenteridentity.account.domain.repository.ProfileRepository;
import com.aieducenter.aieducenteridentity.account.domain.service.AccountPasswordEncoderService;
import com.aieducenter.aieducenteridentity.account.domain.token.AccessTokenClaims;
import com.aieducenter.aieducenteridentity.account.domain.token.AccessTokenSigner;
import com.aieducenter.aieducenteridentity.account.infrastructure.token.JwtTokenProperties;
import com.aieducenter.aieducenteridentity.sso.domain.session.SsoSession;
import com.aieducenter.aieducenteridentity.sso.endpoints.SsoIntegrationTestBase;
import com.jayway.jsonpath.JsonPath;

/**
 * {@code GET /userinfo} HTTP 黑盒集成测试（issue #17 AC）。
 *
 * <p>覆盖：有效 access token → sub + 按 scope 的 profile/email/phone；scope 子集 → 仅对应资料；
 * 缺失/非 Bearer/垃圾/过期/签错/iss 错的 token → 401 invalid_token。</p>
 */
@Transactional
class UserInfoControllerIntegrationTest extends SsoIntegrationTestBase {

    private static final String PASSWORD = "Password123";

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private ProfileRepository profileRepository;

    @Autowired
    private AccountPasswordEncoderService passwordEncoderService;

    @Autowired
    private AccessTokenSigner accessTokenSigner;

    @Autowired
    private JwtTokenProperties jwtTokenProperties;

    /** 建账号（邮箱+手机+密码）+ 个人资料（昵称+头像）。 */
    private Long createAccountWithProfile(String email, String phone, String nickname, String avatar) {
        Account account = Account.register(email, phone, passwordEncoderService.encodePassword(PASSWORD));
        Long userId = accountRepository.save(account).getId();
        profileRepository.save(Profile.create(userId, nickname, avatar));
        return userId;
    }

    /** 走 /authorize（带 scope）→ /token 换 access_token。 */
    private String obtainAccessToken(Long userId, String scope) throws Exception {
        SsoSession session = createSsoSession(userId, "用户");
        MvcResult authorize = mvc.perform(get("/authorize")
                .param("client_id", CLIENT_ID)
                .param("redirect_uri", REDIRECT_URI)
                .param("state", "st")
                .param("nonce", "nonce-xyz")
                .param("scope", scope)
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

    @Test
    void given_valid_token_all_scopes_when_userinfo_then_sub_and_profile_email_phone() throws Exception {
        Long userId = createAccountWithProfile(
            "demo@test.com", "13900112020", "Demo 昵称", "https://cdn/avatar.png");

        String accessToken = obtainAccessToken(userId, "openid profile email phone");

        mvc.perform(get("/userinfo").header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.sub").value(String.valueOf(userId)))
            .andExpect(jsonPath("$.nickname").value("Demo 昵称"))
            .andExpect(jsonPath("$.picture").value("https://cdn/avatar.png"))
            .andExpect(jsonPath("$.email").value("demo@test.com"))
            .andExpect(jsonPath("$.email_verified").value(true))
            .andExpect(jsonPath("$.phone_number").value("13900112020"))
            .andExpect(jsonPath("$.phone_number_verified").value(true));
    }

    @Test
    void given_token_email_scope_only_when_userinfo_then_sub_and_email_but_no_phone_profile() throws Exception {
        Long userId = createAccountWithProfile(
            "only@mail.com", "13900112021", "Nick", "https://cdn/x.png");

        // 只授权 email scope → 只返回 sub + email，不含 phone/profile 资料
        String accessToken = obtainAccessToken(userId, "openid email");

        mvc.perform(get("/userinfo").header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.sub").value(String.valueOf(userId)))
            .andExpect(jsonPath("$.email").value("only@mail.com"))
            .andExpect(jsonPath("$.email_verified").value(true))
            .andExpect(jsonPath("$.phone_number").doesNotExist())
            .andExpect(jsonPath("$.nickname").doesNotExist())
            .andExpect(jsonPath("$.picture").doesNotExist());
    }

    // ── 错误路径：缺失/非 Bearer/垃圾/过期/错 iss/篡改 → 401 invalid_token ─────────

    /** 用签名器直铸自定义 access token（控 iss/exp）。 */
    private String mintToken(String issuer, Instant exp) {
        return accessTokenSigner.sign(new AccessTokenClaims(
            issuer, "1", List.of(jwtTokenProperties.getAudiences().get(0)),
            Instant.now(), exp, "jti-test", "openid"));
    }

    @Test
    void given_no_authorization_header_when_userinfo_then_401_invalid_token() throws Exception {
        mvc.perform(get("/userinfo"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error").value("invalid_token"));
    }

    @Test
    void given_non_bearer_scheme_when_userinfo_then_401_invalid_token() throws Exception {
        mvc.perform(get("/userinfo").header("Authorization", "Basic xyz"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error").value("invalid_token"));
    }

    @Test
    void given_garbage_token_when_userinfo_then_401_invalid_token() throws Exception {
        mvc.perform(get("/userinfo").header("Authorization", "Bearer not.a.jwt"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error").value("invalid_token"));
    }

    @Test
    void given_expired_token_when_userinfo_then_401_invalid_token() throws Exception {
        String expired = mintToken(jwtTokenProperties.getIssuer(), Instant.now().minusSeconds(60));

        mvc.perform(get("/userinfo").header("Authorization", "Bearer " + expired))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error").value("invalid_token"));
    }

    @Test
    void given_token_wrong_issuer_when_userinfo_then_401_invalid_token() throws Exception {
        String foreign = mintToken("https://someone-else.test", Instant.now().plusSeconds(60));

        mvc.perform(get("/userinfo").header("Authorization", "Bearer " + foreign))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error").value("invalid_token"));
    }

    @Test
    void given_tampered_token_when_userinfo_then_401_invalid_token() throws Exception {
        Long userId = createAccountWithProfile("tamper@test.com", "13900112030", "T", null);
        String valid = obtainAccessToken(userId, "openid");
        // 翻转 payload 段（中段）一字节 → 签名覆盖 header.payload，必然不匹配
        String[] parts = valid.split("\\.", 3);
        byte[] payload = java.util.Base64.getUrlDecoder().decode(parts[1]);
        payload[0] ^= 0x01;
        String tampered = parts[0] + "."
            + java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(payload) + "."
            + parts[2];

        mvc.perform(get("/userinfo").header("Authorization", "Bearer " + tampered))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error").value("invalid_token"));
    }
}
