package com.aieducenter.aieducenteridentity.account.endpoints.controller;

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
import com.cartisan.test.base.ApiTestAssertions;

/**
 * 登录 / 登出 HTTP 黑盒集成测试。
 *
 * <p>覆盖：密码登录、短信码登录、登出、防用户枚举（账号不存在 vs 密码错误响应一致）、
 * 停用/锁定拒登、受保护接口 {@code @RequireAuth}（bug#2）、登录后 SaSession 填充 RequestContext（bug#1）。</p>
 */
@Transactional
class AccountLoginIntegrationTest extends AccountIntegrationTestBase {

    private static final String PASSWORD = "Password123";

    @Autowired
    private AccountRepository accountRepository;

    // ── 密码登录 ──────────────────────────────────────────────────────────────

    @Test
    void given_correct_password_when_login_then_token_returned() throws Exception {
        String phone = "13900100001";
        registerPhoneAccount(phone, PASSWORD);

        mvc.perform(post("/api/account/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginPasswordBody(phone, PASSWORD, getCaptcha())))
            .andExpect(ApiTestAssertions.assertOk())
            .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
            .andExpect(jsonPath("$.data.tokenType").value("Bearer"));
    }

    @Test
    void given_wrong_password_and_unknown_account_when_login_then_same_401_anti_enumeration() throws Exception {
        String phone = "13900100002";
        registerPhoneAccount(phone, PASSWORD);

        // 错密码 → 401
        MvcResult wrong = mvc.perform(post("/api/account/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginPasswordBody(phone, PASSWORD + "x", getCaptcha())))
            .andExpect(status().isUnauthorized())
            .andReturn();

        // 账号不存在 → 401（与错码完全一致，不暴露存在性）
        MvcResult unknown = mvc.perform(post("/api/account/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginPasswordBody("13999999999", PASSWORD, getCaptcha())))
            .andExpect(status().isUnauthorized())
            .andReturn();

        String wrongBody = wrong.getResponse().getContentAsString();
        String unknownBody = unknown.getResponse().getContentAsString();
        // 防枚举：code + message 完全一致
        assertThat(JsonPathCode(wrongBody)).isEqualTo(JsonPathCode(unknownBody));
        assertThat(JsonPathMessage(wrongBody)).isEqualTo(JsonPathMessage(unknownBody));
    }

    @Test
    void given_disabled_account_when_login_then_401() throws Exception {
        String phone = "13900100003";
        registerPhoneAccount(phone, PASSWORD);
        disableAccount(phone);

        mvc.perform(post("/api/account/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginPasswordBody(phone, PASSWORD, getCaptcha())))
            .andExpect(status().isUnauthorized())
            .andExpect(ApiTestAssertions.assertError(401));
    }

    @Test
    void given_locked_account_when_login_then_401() throws Exception {
        String phone = "13900100004";
        registerPhoneAccount(phone, PASSWORD);
        lockAccount(phone);

        mvc.perform(post("/api/account/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginPasswordBody(phone, PASSWORD, getCaptcha())))
            .andExpect(status().isUnauthorized())
            .andExpect(ApiTestAssertions.assertError(401));
    }

    // ── 短信码登录 ────────────────────────────────────────────────────────────

    @Test
    void given_valid_sms_code_when_login_then_token_returned() throws Exception {
        String phone = "13900100005";
        registerPhoneAccount(phone, PASSWORD);
        String code = sendSmsCode(phone, "LOGIN", getCaptcha());

        mvc.perform(post("/api/account/login/sms")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"phone\":\"" + phone + "\",\"code\":\"" + code + "\"}"))
            .andExpect(ApiTestAssertions.assertOk())
            .andExpect(jsonPath("$.data.accessToken").isNotEmpty());
    }

    @Test
    void given_passwordless_account_when_login_by_password_then_401() throws Exception {
        String phone = "13900100008";
        // 注册无密码账号（纯验证码建号）
        String regCode = sendSmsCode(phone, "REGISTER", getCaptcha());
        mvc.perform(post("/api/account/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"phone\":\"" + phone + "\",\"smsVerificationCode\":\"" + regCode + "\"}"))
            .andExpect(ApiTestAssertions.assertOk());

        // 无密码账号用密码登录 → verifyPassword 对 null hash 返回 false → LOGIN_PASSWORD_INCORRECT (401)
        mvc.perform(post("/api/account/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginPasswordBody(phone, "AnyPass123", getCaptcha())))
            .andExpect(status().isUnauthorized())
            .andExpect(ApiTestAssertions.assertError(401));
    }

    // ── bug#2：受保护接口未登录被拒 ────────────────────────────────────────────

    @Test
    void given_no_token_when_access_protected_profile_then_401() throws Exception {
        mvc.perform(get("/api/account/profile"))
            .andExpect(status().isUnauthorized());
    }

    // ── bug#1：登录后 SaSession 填充 → RequestContext.userId 可用 ────────────────

    @Test
    void given_logged_in_when_access_profile_then_returns_current_user() throws Exception {
        String phone = "13900100006";
        registerPhoneAccount(phone, PASSWORD);
        String token = loginAndExtractToken(phone, PASSWORD);

        // 登录后带 token 访问受保护接口 → 200，返回当前登录用户资料
        // （证明 SecurityFilter 从 SaSession 读出 userId 写入 RequestContext，bug#1 修复）
        mvc.perform(get("/api/account/profile").header("Authorization", token))
            .andExpect(ApiTestAssertions.assertOk())
            .andExpect(jsonPath("$.data.phone").value(phone));
    }

    // ── 登出 ──────────────────────────────────────────────────────────────────

    @Test
    void given_logged_in_when_logout_then_session_invalidated() throws Exception {
        String phone = "13900100007";
        registerPhoneAccount(phone, PASSWORD);
        String token = loginAndExtractToken(phone, PASSWORD);

        mvc.perform(post("/api/account/logout").header("Authorization", token))
            .andExpect(ApiTestAssertions.assertOk());

        // 登出后再用同一 token 访问受保护接口 → 401（会话已失效）
        mvc.perform(get("/api/account/profile").header("Authorization", token))
            .andExpect(status().isUnauthorized());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private String loginAndExtractToken(String phone, String password) throws Exception {
        MvcResult result = mvc.perform(post("/api/account/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginPasswordBody(phone, password, getCaptcha())))
            .andExpect(ApiTestAssertions.assertOk())
            .andReturn();
        return extractAccessToken(result);
    }

    private void disableAccount(String phone) {
        Account account = accountRepository.findByPhone(phone).orElseThrow();
        account.disable();
        accountRepository.save(account);
    }

    private void lockAccount(String phone) {
        Account account = accountRepository.findByPhone(phone).orElseThrow();
        account.lock();
        accountRepository.save(account);
    }

    private static Integer JsonPathCode(String body) {
        return com.jayway.jsonpath.JsonPath.read(body, "$.code");
    }

    private static String JsonPathMessage(String body) {
        return com.jayway.jsonpath.JsonPath.read(body, "$.message");
    }
}
