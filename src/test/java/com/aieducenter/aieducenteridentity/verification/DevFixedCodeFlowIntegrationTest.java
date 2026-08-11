package com.aieducenter.aieducenteridentity.verification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import com.aieducenter.aieducenteridentity.sso.endpoints.SsoIntegrationTestBase;
import com.jayway.jsonpath.JsonPath;

/**
 * dev 固定码端到端集成测试（issue #29 AC1-AC4）。
 *
 * <p>测试上下文配置固定码（{@code verification.code.dev-code=246810} /
 * {@code verification.captcha.dev-code=qa58}），验证「生成处固定」下比对路径全真：
 * 图形码固定值过校验、验证码注册/登录全通、一次性消费与冷却限流不被绕过。</p>
 *
 * <p>独立测试上下文（{@link TestPropertySource}），不影响默认上下文里
 * CapturingMessageSender 真随机码路径（AC6）。</p>
 */
@Transactional
@TestPropertySource(properties = {
    "verification.code.dev-code=" + DevFixedCodeFlowIntegrationTest.DEV_CODE,
    "verification.captcha.dev-code=" + DevFixedCodeFlowIntegrationTest.DEV_CAPTCHA
})
class DevFixedCodeFlowIntegrationTest extends SsoIntegrationTestBase {

    static final String DEV_CODE = "246810";
    static final String DEV_CAPTCHA = "qa58";

    private static final String EMAIL = "devfixed@aieducenter.com";
    private static final String PHONE = "13800138001";
    private static final String PASSWORD = "Password123";

    /** GET /api/sso/captcha 取 captchaId，并断言 Redis 落地真值 = 固定图形码。 */
    private String createCaptchaAndAssertFixed() throws Exception {
        MvcResult result = mvc.perform(get("/api/sso/captcha"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.image").isNotEmpty())
            .andReturn();
        String captchaId = JsonPath.read(result.getResponse().getContentAsString(), "$.data.captchaId");
        assertThat(redisTemplate.opsForValue().get("captcha:" + captchaId)).isEqualTo(DEV_CAPTCHA);
        return captchaId;
    }

    private void sendSms(String captchaId, String captchaCode, int expectedStatus) throws Exception {
        mvc.perform(post("/api/sso/verification-code/sms")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"phone\":\"" + PHONE + "\",\"purpose\":\"REGISTER\","
                    + "\"captchaId\":\"" + captchaId + "\",\"captchaCode\":\"" + captchaCode + "\"}"))
            .andExpect(status().is(expectedStatus));
    }

    // ========== AC1：图形码生成处固定——固定值过校验，其它值 400 ==========

    @Test
    void given_fixed_captcha_when_send_sms_with_fixed_value_then_ok() throws Exception {
        // given — Redis 真值 = qa58（生成处固定）
        String captchaId = createCaptchaAndAssertFixed();

        // when & then — 固定值过图形码校验，发短信成功
        sendSms(captchaId, DEV_CAPTCHA, 200);
        assertThat(capturingMessageSender.lastCodeFor(PHONE)).isEqualTo(DEV_CODE);
    }

    @Test
    void given_fixed_captcha_when_send_sms_with_other_value_then_400() throws Exception {
        // given
        String captchaId = createCaptchaAndAssertFixed();

        // when & then — 固定 ≠ 放行：其它值照样 400（比对全真）
        sendSms(captchaId, "WRONG", 400);
    }

    // ========== AC2/AC3：固定码注册 + 登录全通；一次性消费仍真 ==========

    @Test
    void given_dev_code_when_register_and_login_code_then_success_and_second_use_400() throws Exception {
        // 发注册码 → 捕获值 = 246810（生成处固定，通道照常投递）
        String registerCode = sendEmailCode(EMAIL, "REGISTER");
        assertThat(registerCode).isEqualTo(DEV_CODE);

        // 注册（email + 固定码 + 密码）→ 建号建会话，200 {redirectUrl}
        mvc.perform(post("/api/sso/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"clientId\":\"" + CLIENT_ID + "\",\"redirectUri\":\"" + REDIRECT_URI + "\","
                    + "\"state\":\"st\",\"nonce\":\"non\","
                    + "\"email\":\"" + EMAIL + "\",\"emailCode\":\"" + DEV_CODE + "\","
                    + "\"password\":\"" + PASSWORD + "\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.redirectUrl", org.hamcrest.Matchers.startsWith(REDIRECT_URI + "?")));
        assertThat(accountRepository.findByEmail(EMAIL)).isPresent();

        // 发登录码 → login-code 填 246810 → 200
        String loginCode = sendEmailCode(EMAIL, "LOGIN");
        assertThat(loginCode).isEqualTo(DEV_CODE);
        mvc.perform(post("/api/sso/login-code")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginCodeBody(DEV_CODE)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.redirectUrl", org.hamcrest.Matchers.startsWith(REDIRECT_URI + "?")));

        // 同一联络方式同 purpose，246810 用第二次 → 400（一次性消费仍真）
        mvc.perform(post("/api/sso/login-code")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginCodeBody(DEV_CODE)))
            .andExpect(status().isBadRequest());
    }

    // ========== AC4：冷却限流仍真 ==========

    @Test
    void given_dev_code_when_resend_within_cooldown_then_429() throws Exception {
        // given — 首次发送成功
        sendEmailCode(EMAIL, "REGISTER");

        // when & then — 60s 冷却期内同邮箱同 purpose 重发 → 429（限流不被固定码绕过）
        mvc.perform(post("/api/sso/verification-code/email")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + EMAIL + "\",\"purpose\":\"REGISTER\"}"))
            .andExpect(status().isTooManyRequests());
    }

    private String loginCodeBody(String code) {
        return "{\"clientId\":\"" + CLIENT_ID + "\",\"redirectUri\":\"" + REDIRECT_URI + "\","
            + "\"state\":\"st\",\"nonce\":\"non\",\"account\":\"" + EMAIL + "\",\"code\":\"" + code + "\"}";
    }
}
