package com.aieducenter.aieducenteridentity.account.endpoints.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

import com.cartisan.test.base.ApiTestAssertions;

/**
 * 密码管理 HTTP 黑盒集成测试——重置密码（验证码）+ 修改密码（验旧密码）。
 */
@Transactional
class AccountPasswordIntegrationTest extends AccountIntegrationTestBase {

    private static final String OLD_PASSWORD = "OldPass123";
    private static final String NEW_PASSWORD = "NewPass456";

    // ── 重置密码 ──────────────────────────────────────────────────────────────

    @Test
    void given_phone_reset_code_when_reset_password_then_new_password_works() throws Exception {
        String phone = "13700100001";
        registerPhoneAccount(phone, OLD_PASSWORD);
        String code = sendSmsCode(phone, "RESET_PASSWORD", getCaptcha());

        mvc.perform(post("/api/account/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"account\":\"" + phone + "\",\"verificationCode\":\"" + code + "\","
                    + "\"newPassword\":\"" + NEW_PASSWORD + "\"}"))
            .andExpect(ApiTestAssertions.assertOk());

        // 新密码能登录
        mvc.perform(post("/api/account/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginPasswordBody(phone, NEW_PASSWORD, getCaptcha())))
            .andExpect(ApiTestAssertions.assertOk())
            .andExpect(jsonPath("$.data.accessToken").isNotEmpty());
    }

    @Test
    void given_wrong_reset_code_when_reset_password_then_400() throws Exception {
        mvc.perform(post("/api/account/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"account\":\"13700100002\",\"verificationCode\":\"000000\","
                    + "\"newPassword\":\"" + NEW_PASSWORD + "\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(ApiTestAssertions.assertError(400));
    }

    @Test
    void given_email_reset_code_when_reset_password_then_new_password_works() throws Exception {
        String email = "reset@example.com";
        // 邮箱注册账号
        String regCode = sendEmailCode(email, "REGISTER");
        mvc.perform(post("/api/account/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + OLD_PASSWORD + "\","
                    + "\"emailVerificationCode\":\"" + regCode + "\"}"))
            .andExpect(ApiTestAssertions.assertOk());

        // 邮箱验证码重置
        String resetCode = sendEmailCode(email, "RESET_PASSWORD");
        mvc.perform(post("/api/account/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"account\":\"" + email + "\",\"verificationCode\":\"" + resetCode + "\","
                    + "\"newPassword\":\"" + NEW_PASSWORD + "\"}"))
            .andExpect(ApiTestAssertions.assertOk());

        // 用新密码 + 邮箱登录成功
        mvc.perform(post("/api/account/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginPasswordBody(email, NEW_PASSWORD, getCaptcha())))
            .andExpect(ApiTestAssertions.assertOk());
    }

    // ── 修改密码 ──────────────────────────────────────────────────────────────

    @Test
    void given_correct_old_password_when_change_password_then_new_password_works() throws Exception {
        String phone = "13700100003";
        registerPhoneAccount(phone, OLD_PASSWORD);
        String token = loginAndExtractToken(phone, OLD_PASSWORD);

        mvc.perform(post("/api/account/change-password")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"oldPassword\":\"" + OLD_PASSWORD + "\",\"newPassword\":\"" + NEW_PASSWORD + "\"}"))
            .andExpect(ApiTestAssertions.assertOk());

        // 新密码能登录（旧会话已被踢出，重新登录）
        mvc.perform(post("/api/account/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginPasswordBody(phone, NEW_PASSWORD, getCaptcha())))
            .andExpect(ApiTestAssertions.assertOk());
    }

    @Test
    void given_wrong_old_password_when_change_password_then_400() throws Exception {
        String phone = "13700100004";
        registerPhoneAccount(phone, OLD_PASSWORD);
        String token = loginAndExtractToken(phone, OLD_PASSWORD);

        mvc.perform(post("/api/account/change-password")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"oldPassword\":\"WrongOld999\",\"newPassword\":\"" + NEW_PASSWORD + "\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(ApiTestAssertions.assertError(400));
    }

    @Test
    void given_new_same_as_old_when_change_password_then_400() throws Exception {
        String phone = "13700100005";
        registerPhoneAccount(phone, OLD_PASSWORD);
        String token = loginAndExtractToken(phone, OLD_PASSWORD);

        mvc.perform(post("/api/account/change-password")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"oldPassword\":\"" + OLD_PASSWORD + "\",\"newPassword\":\"" + OLD_PASSWORD + "\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(ApiTestAssertions.assertError(400));
    }

    @Test
    void given_no_token_when_change_password_then_401() throws Exception {
        mvc.perform(post("/api/account/change-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"oldPassword\":\"x\",\"newPassword\":\"" + NEW_PASSWORD + "\"}"))
            .andExpect(status().isUnauthorized());
    }

    private String loginAndExtractToken(String phone, String password) throws Exception {
        return extractAccessToken(mvc.perform(post("/api/account/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginPasswordBody(phone, password, getCaptcha())))
            .andExpect(ApiTestAssertions.assertOk())
            .andReturn());
    }
}
