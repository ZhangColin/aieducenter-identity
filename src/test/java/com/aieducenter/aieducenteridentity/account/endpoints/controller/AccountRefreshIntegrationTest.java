package com.aieducenter.aieducenteridentity.account.endpoints.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import com.cartisan.test.base.ApiTestAssertions;

/**
 * refresh_token 轮换 / {@code /api/account/refresh} HTTP 黑盒集成测试（issue #13）。
 *
 * <p>覆盖：登录发 refresh、/refresh 换新三件套、一次性轮换（旧 refresh 失效、新 refresh 可继续）、
 * 非法 refresh 401、新 access JWT 兼作会话 token 访问 /profile、/refresh 无需登录态。</p>
 */
@Transactional
class AccountRefreshIntegrationTest extends AccountIntegrationTestBase {

    private static final String PASSWORD = "Password123";

    @Test
    void given_valid_refresh_when_refresh_then_new_three_tokens_and_old_invalid() throws Exception {
        String phone = "13900200001";
        registerPhoneAccount(phone, PASSWORD);
        String refresh = loginAndExtractRefresh(phone, PASSWORD);

        // /refresh 换新 access + id + refresh（请求不带 Authorization，证明 /refresh 无需登录态）
        MvcResult result = mvc.perform(post("/api/account/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(refreshBody(refresh)))
            .andExpect(ApiTestAssertions.assertOk())
            .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
            .andExpect(jsonPath("$.data.idToken").isNotEmpty())
            .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
            .andReturn();

        String newAccess = extractAccessToken(result);
        String newRefresh = extractRefreshToken(result);
        assertThat(jwtHeaderAlg(newAccess)).isEqualTo("RS256");
        // 轮换：新 refresh ≠ 旧 refresh
        assertThat(newRefresh).isNotEqualTo(refresh);

        // 注：issue #15 起 /profile 改凭 SSO cookie（不再以 access JWT 兼 sa-token 会话 token 访问）；
        // access JWT 仅作 OIDC 产物给消费方 BFF，故此处不再用它访问 /profile。

        // 新 refresh 可继续换新（链式轮换）
        mvc.perform(post("/api/account/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(refreshBody(newRefresh)))
            .andExpect(ApiTestAssertions.assertOk());

        // 旧 refresh 已被 consume（原子取删）→ 再用 401
        mvc.perform(post("/api/account/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(refreshBody(refresh)))
            .andExpect(status().isUnauthorized())
            .andExpect(ApiTestAssertions.assertError(401));
    }

    @Test
    void given_invalid_refresh_when_refresh_then_401() throws Exception {
        mvc.perform(post("/api/account/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(refreshBody("not-a-real-refresh-token")))
            .andExpect(status().isUnauthorized())
            .andExpect(ApiTestAssertions.assertError(401));
    }

    @Test
    void given_no_authorization_header_when_refresh_with_valid_token_then_ok() throws Exception {
        // /refresh 被登录拦截器排除：凭 refresh_token 本身鉴权，不带 Authorization 也能 200
        String phone = "13900200002";
        registerPhoneAccount(phone, PASSWORD);
        String refresh = loginAndExtractRefresh(phone, PASSWORD);

        mvc.perform(post("/api/account/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(refreshBody(refresh)))
            .andExpect(ApiTestAssertions.assertOk());
    }

    private String loginAndExtractRefresh(String phone, String password) throws Exception {
        MvcResult result = mvc.perform(post("/api/account/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginPasswordBody(phone, password, getCaptcha())))
            .andExpect(ApiTestAssertions.assertOk())
            .andReturn();
        return extractRefreshToken(result);
    }

    private static String refreshBody(String refreshToken) {
        return "{\"refreshToken\":\"" + refreshToken + "\"}";
    }
}
