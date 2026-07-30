package com.aieducenter.aieducenteridentity.verification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.aieducenter.aieducenteridentity.test.IdentityIntegrationTestBase;
import com.cartisan.test.base.ApiTestAssertions;

/**
 * 图形验证码端到端集成测试（Testcontainers 真 Redis）。
 *
 * <p>覆盖：GET 创建 → Redis 落地（明文 code + TTL）→ 校验三态（正确 / 错码 / 一次性重用）。
 * 校验经「发短信」端点驱动——这是唯一会调用 captchaAppService.verifyCaptcha 的入口。
 */
class CaptchaFlowIntegrationTest extends IdentityIntegrationTestBase {

    private static final String CAPTCHA_KEY_PREFIX = "captcha:";
    private static final String PHONE = "13800138000";

    /** 发短信请求体（captchaCode 由调用方填）。 */
    private String smsBody(String captchaId, String captchaCode) {
        return "{\"phone\":\"" + PHONE + "\",\"purpose\":\"REGISTER\","
            + "\"captchaId\":\"" + captchaId + "\",\"captchaCode\":\"" + captchaCode + "\"}";
    }

    /** GET /api/captcha，返回 {id, 从 Redis 读出的真码}。 */
    private CaptchaInfo createCaptcha() throws Exception {
        var result = mvc.perform(get("/api/captcha"))
            .andExpect(ApiTestAssertions.assertOk())
            .andExpect(jsonPath("$.data.image").isNotEmpty())
            .andExpect(jsonPath("$.data.captchaId").isNotEmpty())
            .andReturn();

        String body = result.getResponse().getContentAsString();
        String captchaId = com.jayway.jsonpath.JsonPath.read(body, "$.data.captchaId");
        String code = redisTemplate.opsForValue().get(CAPTCHA_KEY_PREFIX + captchaId);
        return new CaptchaInfo(captchaId, code);
    }

    @Test
    void given_get_captcha_then_image_and_id_returned_and_code_stored_in_redis() throws Exception {
        // when
        CaptchaInfo captcha = createCaptcha();

        // then — Redis 落地：明文 code 非空 + TTL > 0
        assertThat(captcha.code()).isNotBlank();
        assertThat(redisTemplate.getExpire(CAPTCHA_KEY_PREFIX + captcha.id())).isPositive();
    }

    @Test
    void given_correct_captcha_when_send_sms_then_success() throws Exception {
        // given
        CaptchaInfo captcha = createCaptcha();

        // when & then — 正确图形码 → 发短信成功
        mvc.perform(post("/api/account/verification-code/sms")
                .contentType(MediaType.APPLICATION_JSON)
                .content(smsBody(captcha.id(), captcha.code())))
            .andExpect(ApiTestAssertions.assertOk());
    }

    @Test
    void given_wrong_captcha_when_send_sms_then_400() throws Exception {
        // given
        CaptchaInfo captcha = createCaptcha();

        // when & then — 错图形码 → CAPTCHA_INVALID 400
        mvc.perform(post("/api/account/verification-code/sms")
                .contentType(MediaType.APPLICATION_JSON)
                .content(smsBody(captcha.id(), "WRONG")))
            .andExpect(status().isBadRequest())
            .andExpect(ApiTestAssertions.assertError(400));
    }

    @Test
    void given_reused_captcha_when_send_sms_then_400() throws Exception {
        // given — 第一次用掉 captcha（校验即删除）
        CaptchaInfo captcha = createCaptcha();
        mvc.perform(post("/api/account/verification-code/sms")
                .contentType(MediaType.APPLICATION_JSON)
                .content(smsBody(captcha.id(), captcha.code())))
            .andExpect(ApiTestAssertions.assertOk());

        // when & then — 同一 captcha 二次使用 → 已删除 → 400（一次性）
        mvc.perform(post("/api/account/verification-code/sms")
                .contentType(MediaType.APPLICATION_JSON)
                .content(smsBody(captcha.id(), captcha.code())))
            .andExpect(status().isBadRequest())
            .andExpect(ApiTestAssertions.assertError(400));

        // and — Redis 里 captcha 已被删除
        assertThat(redisTemplate.hasKey(CAPTCHA_KEY_PREFIX + captcha.id())).isFalse();
    }

    private record CaptchaInfo(String id, String code) {}
}
