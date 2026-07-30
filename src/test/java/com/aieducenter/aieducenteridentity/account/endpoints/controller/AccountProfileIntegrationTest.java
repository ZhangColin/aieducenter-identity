package com.aieducenter.aieducenteridentity.account.endpoints.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import com.cartisan.test.base.ApiTestAssertions;

/**
 * 个人资料 HTTP 黑盒集成测试——查看 / 编辑（需登录态）。
 */
@Transactional
class AccountProfileIntegrationTest extends AccountIntegrationTestBase {

    private static final String PASSWORD = "Password123";

    @Test
    void given_logged_in_when_get_profile_then_returns_current_user() throws Exception {
        String phone = "13600100001";
        registerPhoneAccount(phone, PASSWORD);
        String token = loginAndExtractToken(phone);

        mvc.perform(get("/api/account/profile").header("Authorization", token))
            .andExpect(ApiTestAssertions.assertOk())
            .andExpect(jsonPath("$.data.userId").isString()) // TSID Long 序列化为字符串
            .andExpect(jsonPath("$.data.phone").value(phone))
            .andExpect(jsonPath("$.data.nickname").value(phone)); // 无昵称时默认回退到手机号
    }

    @Test
    void given_logged_in_when_update_nickname_then_get_reflects_change() throws Exception {
        String phone = "13600100002";
        registerPhoneAccount(phone, PASSWORD);
        String token = loginAndExtractToken(phone);

        mvc.perform(put("/api/account/profile")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nickname\":\"Colin\",\"avatar\":\"https://cdn/avatar.png\"}"))
            .andExpect(ApiTestAssertions.assertOk());

        mvc.perform(get("/api/account/profile").header("Authorization", token))
            .andExpect(ApiTestAssertions.assertOk())
            .andExpect(jsonPath("$.data.nickname").value("Colin"))
            .andExpect(jsonPath("$.data.avatar").value("https://cdn/avatar.png"));
    }

    @Test
    void given_no_token_when_get_profile_then_401() throws Exception {
        mvc.perform(get("/api/account/profile"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void given_no_token_when_update_profile_then_401() throws Exception {
        mvc.perform(put("/api/account/profile")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nickname\":\"X\"}"))
            .andExpect(status().isUnauthorized());
    }

    private String loginAndExtractToken(String phone) throws Exception {
        MvcResult result = mvc.perform(post("/api/account/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginPasswordBody(phone, PASSWORD, getCaptcha())))
            .andExpect(ApiTestAssertions.assertOk())
            .andReturn();
        return extractAccessToken(result);
    }
}
