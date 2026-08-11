package com.aieducenter.aieducenteridentity.account.endpoints.controller;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import com.aieducenter.aieducenteridentity.test.IdentityIntegrationTestBase;
import com.cartisan.test.base.ApiTestAssertions;
import com.jayway.jsonpath.JsonPath;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Account HTTP 黑盒集成测试基类。
 *
 * <p>背靠 {@link IdentityIntegrationTestBase}（Testcontainers 真 PG+Redis），驱动「真实」验证链路：
 * 取图形验证码 → 发短信/邮箱码 → 内存捕获真实码 → 用于重置密码。不做 service 层 mock。</p>
 */
abstract class AccountIntegrationTestBase extends IdentityIntegrationTestBase {

    private static final String CAPTCHA_KEY_PREFIX = "captcha:";

    /** 取一个图形验证码，返回 {id, 真码}（真码从 Redis 读出）。 */
    protected Captcha getCaptcha() throws Exception {
        MvcResult result = mvc.perform(get("/api/sso/captcha"))
            .andExpect(ApiTestAssertions.assertOk())
            .andReturn();
        String body = result.getResponse().getContentAsString();
        String captchaId = JsonPath.read(body, "$.data.captchaId");
        String code = redisTemplate.opsForValue().get(CAPTCHA_KEY_PREFIX + captchaId);
        return new Captcha(captchaId, code);
    }

    /** 发短信验证码并返回内存捕获到的真实码。 */
    protected String sendSmsCode(String phone, String purpose, Captcha captcha) throws Exception {
        mvc.perform(post("/api/sso/verification-code/sms")
                .contentType(MediaType.APPLICATION_JSON)
                .content(smsCodeBody(phone, purpose, captcha)))
            .andExpect(ApiTestAssertions.assertOk());
        return capturingMessageSender.lastCodeFor(phone);
    }

    /** 发邮箱验证码并返回内存捕获到的真实码。 */
    protected String sendEmailCode(String email, String purpose) throws Exception {
        mvc.perform(post("/api/sso/verification-code/email")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"purpose\":\"" + purpose + "\"}"))
            .andExpect(ApiTestAssertions.assertOk());
        return capturingMessageSender.lastCodeFor(email);
    }

    private static String smsCodeBody(String phone, String purpose, Captcha captcha) {
        return "{\"phone\":\"" + phone + "\",\"purpose\":\"" + purpose + "\","
            + "\"captchaId\":\"" + captcha.id() + "\",\"captchaCode\":\"" + captcha.code() + "\"}";
    }

    protected record Captcha(String id, String code) {}
}
