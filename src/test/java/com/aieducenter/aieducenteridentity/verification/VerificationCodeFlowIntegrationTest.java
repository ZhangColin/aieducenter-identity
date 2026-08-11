package com.aieducenter.aieducenteridentity.verification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.aieducenter.aieducenteridentity.test.IdentityIntegrationTestBase;
import com.cartisan.test.base.ApiTestAssertions;

/**
 * 验证码端到端集成测试（Testcontainers 真 Redis）。
 *
 * <p>覆盖邮箱验证码全流程（下发 → 捕获 → 校验 / 已用 / 错码 / 邮箱限流）、IP 限流，
 * 以及 bug#3 核心回归：短信验证码走真实生成码（捕获码 == Redis 落地码 ≠ 硬编码 123456）。
 */
class VerificationCodeFlowIntegrationTest extends IdentityIntegrationTestBase {

    private static final String PHONE = "13800138000";
    private static final String VERIFICATION_KEY = "verification:" + PHONE + ":REGISTER";

    // ========== 邮箱验证码 ==========

    /** 发邮件验证码，返回捕获到的真实码。 */
    private String sendEmailCode(String email) throws Exception {
        mvc.perform(post("/api/sso/verification-code/email")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"purpose\":\"REGISTER\"}"))
            .andExpect(ApiTestAssertions.assertOk());
        return capturingMessageSender.lastCodeFor(email);
    }

    private void verifyEmailCode(String email, String code, int expectedErrorStatus) throws Exception {
        var actions = mvc.perform(post("/api/sso/verification-code/verify")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"" + email + "\",\"code\":\"" + code + "\",\"purpose\":\"REGISTER\"}"));
        if (expectedErrorStatus == 200) {
            actions.andExpect(ApiTestAssertions.assertOk());
        } else {
            actions.andExpect(status().is(expectedErrorStatus))
                   .andExpect(ApiTestAssertions.assertError(expectedErrorStatus));
        }
    }

    @Test
    void given_send_email_code_then_captured_and_verify_succeeds() throws Exception {
        // when
        String code = sendEmailCode("alice@example.com");
        assertThat(code).matches("\\d{6}");

        // then — 用捕获的真实码校验通过
        verifyEmailCode("alice@example.com", code, 200);
    }

    @Test
    void given_already_used_code_when_verify_again_then_400() throws Exception {
        // given — 发码并校验成功（一次性消费）
        String code = sendEmailCode("bob@example.com");
        verifyEmailCode("bob@example.com", code, 200);

        // when & then — 同一码再校验 → 已使用 400
        verifyEmailCode("bob@example.com", code, 400);
    }

    @Test
    void given_wrong_code_when_verify_then_400() throws Exception {
        // given
        String realCode = sendEmailCode("carol@example.com");

        // when & then — 错码（与真码不同）→ 400
        String wrongCode = realCode.equals("000000") ? "111111" : "000000";
        verifyEmailCode("carol@example.com", wrongCode, 400);
    }

    @Test
    void given_same_email_resend_within_cooldown_then_429() throws Exception {
        // given — 首次发送成功
        sendEmailCode("dave@example.com");

        // when & then — 60s 冷却期内同邮箱再发 → 邮箱限流 429
        mvc.perform(post("/api/sso/verification-code/email")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"dave@example.com\",\"purpose\":\"REGISTER\"}"))
            .andExpect(status().isTooManyRequests())
            .andExpect(ApiTestAssertions.assertError(429));
    }

    @Test
    void given_ip_exceeds_hourly_limit_when_send_then_429() throws Exception {
        // given — ipMaxPerHour=10；用不同邮箱绕过邮箱限流，仅让 IP 计数累积
        for (int i = 1; i <= 10; i++) {
            sendEmailCode("ip" + i + "@example.com");
        }

        // when & then — 第 11 次（同 IP）→ IP 限流 429
        mvc.perform(post("/api/sso/verification-code/email")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"ip11@example.com\",\"purpose\":\"REGISTER\"}"))
            .andExpect(status().isTooManyRequests())
            .andExpect(ApiTestAssertions.assertError(429));
    }

    // ========== 短信验证码（bug#3 核心回归） ==========

    @Test
    void given_send_sms_code_then_captured_equals_redis_and_not_hardcoded() throws Exception {
        // given — 取图形验证码（发短信前置）
        var captchaResult = mvc.perform(get("/api/sso/captcha"))
            .andExpect(ApiTestAssertions.assertOk())
            .andReturn();
        String body = captchaResult.getResponse().getContentAsString();
        String captchaId = com.jayway.jsonpath.JsonPath.read(body, "$.data.captchaId");
        String captchaCode = redisTemplate.opsForValue().get("captcha:" + captchaId);

        // when — 发短信
        mvc.perform(post("/api/sso/verification-code/sms")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"phone\":\"" + PHONE + "\",\"purpose\":\"REGISTER\","
                    + "\"captchaId\":\"" + captchaId + "\",\"captchaCode\":\"" + captchaCode + "\"}"))
            .andExpect(ApiTestAssertions.assertOk());

        // then — bug#3：发送端收到的码 == Redis 落地的码 == 真实生成码（6 位数字，绝非硬编码 123456）
        String sentCode = capturingMessageSender.lastCodeFor(PHONE);
        String storedCode = (String) redisTemplate.opsForHash().get(VERIFICATION_KEY, "code");

        assertThat(sentCode).isNotNull();
        assertThat(sentCode).isNotEqualTo("123456");
        assertThat(sentCode).matches("\\d{6}");
        assertThat(sentCode).isEqualTo(storedCode);
    }
}
