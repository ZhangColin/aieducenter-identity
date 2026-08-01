package com.aieducenter.aieducenteridentity.account.endpoints.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

import com.aieducenter.aieducenteridentity.sso.domain.session.SsoSession;
import com.cartisan.test.base.ApiTestAssertions;

import jakarta.servlet.http.Cookie;

/**
 * 密码管理 HTTP 黑盒集成测试——重置密码（验证码，公开）+ 修改密码（验旧密码，凭 SSO cookie）。
 *
 * <p>改密端点凭 SSO cookie 经 SSO 会话过滤器认人（issue #21：摘掉 {@code @RequireAuth}，
 * 旧 {@code /api/account/login} 链路已拆，建号/登录在测试里走 repository + {@code /api/auth/login}）。</p>
 */
@Transactional
class AccountPasswordIntegrationTest extends AccountIntegrationTestBase {

    private static final String OLD_PASSWORD = "OldPass123";
    private static final String NEW_PASSWORD = "NewPass456";

    /** 建 SSO 会话并装入 cookie（改密认人入口 = SSO 会话过滤器）。 */
    private Cookie ssoLoginCookie(Long userId, String displayName) {
        SsoSession session = createSsoSession(userId, displayName);
        return ssoCookie(session.sessionId());
    }

    /** 新密码能登录 = SSO 密码登录 302 发 code（唯一认证入口 /api/auth/login）。 */
    private void assertPasswordLoginWorks(String account, String password) throws Exception {
        mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"clientId\":\"" + CLIENT_ID + "\",\"redirectUri\":\"" + REDIRECT_URI + "\","
                    + "\"account\":\"" + account + "\",\"password\":\"" + password + "\"}"))
            .andExpect(status().isFound());
    }

    // ── 重置密码 ──────────────────────────────────────────────────────────────

    @Test
    void given_phone_reset_code_when_reset_password_then_new_password_works() throws Exception {
        String phone = "13700100001";
        createPhoneAccount(phone, OLD_PASSWORD);
        String code = sendSmsCode(phone, "RESET_PASSWORD", getCaptcha());

        mvc.perform(post("/api/account/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"account\":\"" + phone + "\",\"verificationCode\":\"" + code + "\","
                    + "\"newPassword\":\"" + NEW_PASSWORD + "\"}"))
            .andExpect(ApiTestAssertions.assertOk());

        assertPasswordLoginWorks(phone, NEW_PASSWORD);
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
        createEmailAccount(email, OLD_PASSWORD);

        String resetCode = sendEmailCode(email, "RESET_PASSWORD");
        mvc.perform(post("/api/account/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"account\":\"" + email + "\",\"verificationCode\":\"" + resetCode + "\","
                    + "\"newPassword\":\"" + NEW_PASSWORD + "\"}"))
            .andExpect(ApiTestAssertions.assertOk());

        assertPasswordLoginWorks(email, NEW_PASSWORD);
    }

    // ── 修改密码（凭 SSO cookie）──────────────────────────────────────────────

    @Test
    void given_correct_old_password_when_change_password_then_new_password_works() throws Exception {
        String phone = "13700100003";
        Long userId = createPhoneAccount(phone, OLD_PASSWORD);
        Cookie sso = ssoLoginCookie(userId, phone);

        mvc.perform(post("/api/account/change-password")
                .cookie(sso)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"oldPassword\":\"" + OLD_PASSWORD + "\",\"newPassword\":\"" + NEW_PASSWORD + "\"}"))
            .andExpect(ApiTestAssertions.assertOk());

        // 旧会话已被踢出（改密踢人 #19，端到端断言见 SsoKickoutIntegrationTest），新密码可登录
        assertPasswordLoginWorks(phone, NEW_PASSWORD);
    }

    @Test
    void given_wrong_old_password_when_change_password_then_400() throws Exception {
        String phone = "13700100004";
        Long userId = createPhoneAccount(phone, OLD_PASSWORD);
        Cookie sso = ssoLoginCookie(userId, phone);

        mvc.perform(post("/api/account/change-password")
                .cookie(sso)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"oldPassword\":\"WrongOld999\",\"newPassword\":\"" + NEW_PASSWORD + "\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(ApiTestAssertions.assertError(400));
    }

    @Test
    void given_new_same_as_old_when_change_password_then_400() throws Exception {
        String phone = "13700100005";
        Long userId = createPhoneAccount(phone, OLD_PASSWORD);
        Cookie sso = ssoLoginCookie(userId, phone);

        mvc.perform(post("/api/account/change-password")
                .cookie(sso)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"oldPassword\":\"" + OLD_PASSWORD + "\",\"newPassword\":\"" + OLD_PASSWORD + "\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(ApiTestAssertions.assertError(400));
    }

    @Test
    void given_no_sso_cookie_when_change_password_then_401() throws Exception {
        mvc.perform(post("/api/account/change-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"oldPassword\":\"x\",\"newPassword\":\"" + NEW_PASSWORD + "\"}"))
            .andExpect(status().isUnauthorized());
    }
}
