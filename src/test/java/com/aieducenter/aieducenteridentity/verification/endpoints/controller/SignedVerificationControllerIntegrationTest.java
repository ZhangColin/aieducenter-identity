package com.aieducenter.aieducenteridentity.verification.endpoints.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import com.aieducenter.aieducenteridentity.test.IdentityIntegrationTestBase;
import com.aieducenter.aieducenteridentity.test.TestSignatureHelper;
import com.aieducenter.aieducenteridentity.test.WireMockAppRegistryConfig;
import com.aieducenter.aieducenteridentity.verification.application.CaptchaAppService;
import com.aieducenter.aieducenteridentity.verification.application.VerificationCodeAppService;
import com.aieducenter.aieducenteridentity.verification.application.dto.CreateCaptchaResponse;
import com.aieducenter.aieducenteridentity.verification.application.dto.SendEmailCodeCommand;
import com.aieducenter.aieducenteridentity.verification.application.dto.SendSmsCodeCommand;
import com.cartisan.test.base.ApiTestAssertions;

/**
 * verification bc 签名服务端点集成测试（#62：{@code POST /api/verification-codes} 发码 ·
 * {@code POST /api/verification-codes/verify} 裸验码）。
 *
 * <p>全链路：签名 gate（{@code @RequireSignature} → {@code SignatureVerificationFilter} / 拦截器 →
 * {@code RemoteApiKeyProvider} 解析 WireMock 预置的 api-keys stub）→ controller 委托本 bc AppService
 * → {@code SendCodeResponse} / {@code {valid}} 响应序列化。</p>
 *
 * <p>薄壳 adapter 不重测领域逻辑（发码 / 限流 / 验码三态 / 图形码已在 AppService 层覆盖）——只验外部
 * HTTP 行为：签名放行、{@code target} 路由（EMAIL/SMS）、图形码可选（带 / 不带都能发短信）、验码老实回
 * {@code valid}、错误响应为标准 {@code ApiResponse}。{@link TestSignatureHelper} 构造合法签名，
 * 凭据取自 {@link WireMockAppRegistryConfig} 的 api-keys stub；验证码经 AppService 真发
 * （{@link com.aieducenter.aieducenteridentity.test.CapturingMessageSender} 捕获真码）后回填。</p>
 *
 * <p>签名 gate 是 controller 级（类级 {@code @RequireSignature}）——发码、验码各取一条篡改签名 → 401
 * 作代表点，覆盖 AC「各端点错签名 → 401」，不重复测框架签名算法本身。</p>
 */
@Transactional
class SignedVerificationControllerIntegrationTest extends IdentityIntegrationTestBase {

    /** 可信调用方签名工具——凭据与 WireMockAppRegistryConfig 的 api-keys stub 同源。 */
    private final TestSignatureHelper signer = new TestSignatureHelper(
        WireMockAppRegistryConfig.SIGNED_CALLER_API_KEY,
        WireMockAppRegistryConfig.SIGNED_CALLER_API_SECRET);

    @Autowired
    private VerificationCodeAppService verificationCodeAppService;

    @Autowired
    private CaptchaAppService captchaAppService;

    /** 带 5 个合法签名头 POST JSON。 */
    private MockHttpServletRequestBuilder signedPost(String path, String body) {
        MockHttpServletRequestBuilder req = post(path)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body);
        signer.sign(body).forEach(req::header);
        return req;
    }

    /** 经 AppService 真发邮箱码（绕过端点，给验码测试回填真码），返回捕获器拿到的真码。 */
    private String seedEmailCode(String email, String purpose) {
        verificationCodeAppService.sendEmailVerificationCode(
            new SendEmailCodeCommand(email, purpose), "127.0.0.1");
        return capturingMessageSender.lastCodeFor(email);
    }

    /** 经 AppService 真发短信码（图形码可选后可不带），返回捕获器拿到的真码。 */
    private String seedSmsCode(String phone, String purpose) {
        verificationCodeAppService.sendSmsVerificationCode(
            new SendSmsCodeCommand(phone, purpose, null, null), "127.0.0.1");
        return capturingMessageSender.lastCodeFor(phone);
    }

    /** 经 AppService 取图形码，从 Redis 读出真码（供发短信端点回填）。 */
    private CaptchaInfo createCaptcha() {
        CreateCaptchaResponse captcha = captchaAppService.createCaptcha();
        String code = redisTemplate.opsForValue().get("captcha:" + captcha.captchaId());
        return new CaptchaInfo(captcha.captchaId(), code);
    }

    // ========== POST /api/verification-codes（发码）==========

    @Test
    void given_valid_signature_and_email_target_when_send_code_then_returns_send_response_and_sends()
        throws Exception {
        String email = "send-email-sig@example.com";
        String body = "{\"target\":\"EMAIL\",\"value\":\"" + email + "\",\"purpose\":\"REGISTER\"}";

        mvc.perform(signedPost("/api/verification-codes", body))
            .andExpect(ApiTestAssertions.assertOk())
            .andExpect(jsonPath("$.data.expireInSeconds").value(300))
            .andExpect(jsonPath("$.data.resentAfterSeconds").value(60));

        // 发码端点经 AppService 真发——捕获器拿到真码
        assertThat(capturingMessageSender.lastCodeFor(email)).isNotBlank();
    }

    @Test
    void given_valid_signature_and_sms_target_without_captcha_when_send_code_then_returns_send_response()
        throws Exception {
        // 关键 AC：短信发码「不强制图形码」——不带 captchaId/captchaCode 也能发
        String phone = "13900150201";
        String body = "{\"target\":\"SMS\",\"value\":\"" + phone + "\",\"purpose\":\"REGISTER\"}";

        mvc.perform(signedPost("/api/verification-codes", body))
            .andExpect(ApiTestAssertions.assertOk())
            .andExpect(jsonPath("$.data.expireInSeconds").value(300));

        assertThat(capturingMessageSender.lastCodeFor(phone)).isNotBlank();
    }

    @Test
    void given_valid_signature_and_sms_target_with_correct_captcha_when_send_code_then_returns_send_response()
        throws Exception {
        // 带正确图形码也能发（调用方自决加防脚本轰炸层）
        CaptchaInfo captcha = createCaptcha();
        String phone = "13900150202";
        String body = "{\"target\":\"SMS\",\"value\":\"" + phone + "\",\"purpose\":\"REGISTER\","
            + "\"captchaId\":\"" + captcha.id() + "\",\"captchaCode\":\"" + captcha.code() + "\"}";

        mvc.perform(signedPost("/api/verification-codes", body))
            .andExpect(ApiTestAssertions.assertOk());
    }

    @Test
    void given_valid_signature_and_sms_target_with_wrong_captcha_when_send_code_then_returns_400()
        throws Exception {
        // 提供图形码但填错 → CAPTCHA_INVALID 400（提供则校验）
        CaptchaInfo captcha = createCaptcha();
        String phone = "13900150203";
        String body = "{\"target\":\"SMS\",\"value\":\"" + phone + "\",\"purpose\":\"REGISTER\","
            + "\"captchaId\":\"" + captcha.id() + "\",\"captchaCode\":\"WRONG\"}";

        mvc.perform(signedPost("/api/verification-codes", body))
            .andExpect(status().isBadRequest())
            .andExpect(ApiTestAssertions.assertError(400));
    }

    @Test
    void given_tampered_signature_when_send_code_then_returns_401() throws Exception {
        String body = "{\"target\":\"EMAIL\",\"value\":\"send-gate@example.com\",\"purpose\":\"REGISTER\"}";

        Map<String, String> headers = signer.sign(body);
        headers.put(TestSignatureHelper.HEADER_SIGN, headers.get(TestSignatureHelper.HEADER_SIGN) + "deadbeef");

        MockHttpServletRequestBuilder req = post("/api/verification-codes")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body);
        headers.forEach(req::header);

        // 类级 @RequireSignature gate：篡改签名 → 401（AC「各端点错签名 → 401」，发码取代表点）
        mvc.perform(req).andExpect(status().isUnauthorized());
        assertThat(capturingMessageSender.lastCodeFor("send-gate@example.com")).isNull();
    }

    // ========== POST /api/verification-codes/verify（裸验码）==========

    @Test
    void given_valid_signature_and_correct_email_code_when_verify_then_returns_valid_true() throws Exception {
        String email = "verify-valid-sig@example.com";
        String code = seedEmailCode(email, "LOGIN");

        String body = "{\"target\":\"EMAIL\",\"value\":\"" + email + "\",\"code\":\"" + code + "\","
            + "\"purpose\":\"LOGIN\"}";

        // 码对 → valid=true（码即标记已用）
        mvc.perform(signedPost("/api/verification-codes/verify", body))
            .andExpect(ApiTestAssertions.assertOk())
            .andExpect(jsonPath("$.data.valid").value(true));
    }

    @Test
    void given_valid_signature_and_correct_sms_code_when_verify_then_returns_valid_true() throws Exception {
        String phone = "13900150301";
        String code = seedSmsCode(phone, "LOGIN");

        String body = "{\"target\":\"SMS\",\"value\":\"" + phone + "\",\"code\":\"" + code + "\","
            + "\"purpose\":\"LOGIN\"}";

        mvc.perform(signedPost("/api/verification-codes/verify", body))
            .andExpect(ApiTestAssertions.assertOk())
            .andExpect(jsonPath("$.data.valid").value(true));
    }

    @Test
    void given_valid_signature_but_wrong_code_when_verify_then_returns_valid_false() throws Exception {
        String email = "verify-invalid-sig@example.com";
        seedEmailCode(email, "LOGIN");

        // 码错 → valid=false（不抛错、老实回；调用方可信，不搬浏览器防枚举）
        String body = "{\"target\":\"EMAIL\",\"value\":\"" + email + "\",\"code\":\"000000\","
            + "\"purpose\":\"LOGIN\"}";

        mvc.perform(signedPost("/api/verification-codes/verify", body))
            .andExpect(ApiTestAssertions.assertOk())
            .andExpect(jsonPath("$.data.valid").value(false));
    }

    @Test
    void given_tampered_signature_when_verify_then_returns_401() throws Exception {
        String body = "{\"target\":\"EMAIL\",\"value\":\"verify-gate@example.com\",\"code\":\"123456\","
            + "\"purpose\":\"LOGIN\"}";

        Map<String, String> headers = signer.sign(body);
        headers.put(TestSignatureHelper.HEADER_SIGN, headers.get(TestSignatureHelper.HEADER_SIGN) + "deadbeef");

        MockHttpServletRequestBuilder req = post("/api/verification-codes/verify")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body);
        headers.forEach(req::header);

        // 类级 @RequireSignature gate：篡改签名 → 401（验码取代表点）
        mvc.perform(req).andExpect(status().isUnauthorized());
    }

    private record CaptchaInfo(String id, String code) {}
}
