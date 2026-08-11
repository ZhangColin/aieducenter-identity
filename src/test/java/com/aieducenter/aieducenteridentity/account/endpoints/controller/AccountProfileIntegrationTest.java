package com.aieducenter.aieducenteridentity.account.endpoints.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import com.aieducenter.aieducenteridentity.account.domain.aggregate.Profile;
import com.aieducenter.aieducenteridentity.account.domain.repository.ProfileRepository;
import com.aieducenter.aieducenteridentity.sso.domain.session.SsoSession;
import com.cartisan.test.base.ApiTestAssertions;

import jakarta.servlet.http.Cookie;

/**
 * 个人资料 HTTP 黑盒集成测试——查看 / 编辑（凭 SSO cookie，issue #15）。
 */
@org.springframework.transaction.annotation.Transactional
class AccountProfileIntegrationTest extends AccountIntegrationTestBase {

    @Autowired
    private ProfileRepository profileRepository;

    /** 直接建号 + profile（注册端点随旧链路拆除，#18 在新链路重建）→ 建 SSO 会话 → 返回 SSO cookie。 */
    private Cookie ssoLoginCookie(String phone) {
        Long userId = createPhoneAccount(phone, "unused-pw");
        profileRepository.save(Profile.create(userId, phone, null));
        SsoSession session = createSsoSession(userId, phone);
        return ssoCookie(session.sessionId());
    }

    @Test
    void given_logged_in_when_get_profile_then_returns_current_user() throws Exception {
        String phone = "13600100001";
        Cookie sso = ssoLoginCookie(phone);

        mvc.perform(get("/api/sso/profile").cookie(sso))
            .andExpect(ApiTestAssertions.assertOk())
            .andExpect(jsonPath("$.data.userId").isString()) // TSID Long 序列化为字符串
            .andExpect(jsonPath("$.data.phone").value(phone))
            .andExpect(jsonPath("$.data.nickname").value(phone)); // 无昵称时默认回退到手机号
    }

    @Test
    void given_logged_in_when_update_nickname_then_get_reflects_change() throws Exception {
        String phone = "13600100002";
        Cookie sso = ssoLoginCookie(phone);

        mvc.perform(put("/api/sso/profile")
                .cookie(sso)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nickname\":\"Colin\",\"avatar\":\"https://cdn/avatar.png\"}"))
            .andExpect(ApiTestAssertions.assertOk());

        mvc.perform(get("/api/sso/profile").cookie(sso))
            .andExpect(ApiTestAssertions.assertOk())
            .andExpect(jsonPath("$.data.nickname").value("Colin"))
            .andExpect(jsonPath("$.data.avatar").value("https://cdn/avatar.png"));
    }

    @Test
    void given_no_token_when_get_profile_then_401() throws Exception {
        mvc.perform(get("/api/sso/profile"))
            .andExpect(status().isUnauthorized());
    }

    // ── /me（issue #12 / #15 凭 SSO cookie）──────────────────────────────────

    @Test
    void given_logged_in_when_get_me_then_returns_current_user() throws Exception {
        String phone = "13600100003";
        Cookie sso = ssoLoginCookie(phone);

        mvc.perform(get("/api/sso/me").cookie(sso))
            .andExpect(ApiTestAssertions.assertOk())
            .andExpect(jsonPath("$.data.userId").isString())
            .andExpect(jsonPath("$.data.phone").value(phone));
    }

    @Test
    void given_no_token_when_get_me_then_401() throws Exception {
        mvc.perform(get("/api/sso/me"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void given_no_token_when_update_profile_then_401() throws Exception {
        mvc.perform(put("/api/sso/profile")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nickname\":\"X\"}"))
            .andExpect(status().isUnauthorized());
    }
}
