package com.aieducenter.aieducenteridentity.account.endpoints.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.aieducenter.aieducenteridentity.account.domain.aggregate.Account;
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
 * account bc 签名服务端点集成测试（#58 authenticate / #59 authenticate-by-code · register · reset-password）。
 *
 * <p>全链路：签名 gate（{@code @RequireSignature} → {@code SignatureVerificationFilter} / 拦截器 →
 * {@code RemoteApiKeyProvider} 解析 WireMock 预置的 api-keys stub）→ controller 委托本 bc AppService
 * → {@code SubjectView} / 204 响应序列化。</p>
 *
 * <p>薄壳 adapter 不重测领域逻辑（验密 / 验码 / 防枚举 / 建号唯一性 / 状态机已在 AppService 层覆盖）——
 * 只验外部 HTTP 行为：签名放行、验码 gate（码错 → 标准 {@code ApiResponse}）、委托到正确 AppService、
 * 响应格式（{@code SubjectView} / 204）、错误响应为标准 {@code ApiResponse}（非 OIDC {@code {error}}）。
 * {@link TestSignatureHelper} 构造合法签名，凭据取自 {@link WireMockAppRegistryConfig} 的 api-keys stub；
 * 验证码经 {@code VerificationCodeAppService} 真发（{@link CapturingMessageSender} 捕获真码）后回填。</p>
 *
 * <p>签名 gate 是 controller 级（类级 {@code @RequireSignature}），{@code authenticate} 的
 * 缺签名头 / 篡改签名 → 401 已证明该 gate 对本 controller 所有端点生效；#59 各端点（authenticate-by-code /
 * register / reset-password）各取一条篡改签名 → 401 作代表点，覆盖 AC「各端点错签名 → 401」，
 * 不再逐端点重复测框架签名算法本身。</p>
 */
@Transactional
class SignedAccountControllerIntegrationTest extends IdentityIntegrationTestBase {

    private static final String PASSWORD = "Pass1234";

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

    /** 经 verification 应用服务真发邮箱码，返回捕获器拿到的真码（供端点回填验码）。 */
    private String sendEmailCode(String email, String purpose) {
        verificationCodeAppService.sendEmailVerificationCode(
            new SendEmailCodeCommand(email, purpose), "127.0.0.1");
        return capturingMessageSender.lastCodeFor(email);
    }

    /** 经 verification 应用服务真发短信码（前置真图形码），返回捕获器拿到的真码。 */
    private String sendSmsCode(String phone, String purpose) {
        CreateCaptchaResponse captcha = captchaAppService.createCaptcha();
        String captchaCode = redisTemplate.opsForValue().get("captcha:" + captcha.captchaId());
        verificationCodeAppService.sendSmsVerificationCode(
            new SendSmsCodeCommand(phone, purpose, captcha.captchaId(), captchaCode), "127.0.0.1");
        return capturingMessageSender.lastCodeFor(phone);
    }

    @Test
    void given_valid_signature_and_correct_credentials_when_authenticate_then_returns_subject_view() throws Exception {
        String email = "auth-sig@example.com";
        Long userId = createEmailAccount(email, PASSWORD);

        String body = "{\"identifier\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}";

        mvc.perform(signedPost("/api/account/authenticate", body))
            .andExpect(ApiTestAssertions.assertOk())
            .andExpect(jsonPath("$.data.userId").value(userId))
            .andExpect(jsonPath("$.data.email").value(email))
            .andExpect(jsonPath("$.data.status").value("USABLE"));
    }

    @Test
    void given_valid_signature_but_wrong_password_when_authenticate_then_returns_api_response_error_not_oidc()
        throws Exception {
        String email = "auth-sig-wrong@example.com";
        createEmailAccount(email, PASSWORD);

        String body = "{\"identifier\":\"" + email + "\",\"password\":\"WrongPass999\"}";

        // 标准 ApiResponse 错误响应（$.code = 401 数值），而非 OIDC 的 {error, error_description}
        mvc.perform(signedPost("/api/account/authenticate", body))
            .andExpect(status().isUnauthorized())
            .andExpect(ApiTestAssertions.assertError(401))
            .andExpect(jsonPath("$.error").doesNotExist());
    }

    @Test
    void given_no_signature_headers_when_authenticate_then_returns_401() throws Exception {
        String body = "{\"identifier\":\"no-sig@example.com\",\"password\":\"" + PASSWORD + "\"}";

        // 无任何签名头 → @RequireSignature 拦截器挡下，401
        mvc.perform(post("/api/account/authenticate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void given_tampered_signature_when_authenticate_then_returns_401() throws Exception {
        String body = "{\"identifier\":\"tampered@example.com\",\"password\":\"" + PASSWORD + "\"}";

        Map<String, String> headers = signer.sign(body);
        // 篡改 X-Sign：与计算出的签名不一致 → HMAC 校验失败 → 401
        headers.put(TestSignatureHelper.HEADER_SIGN, headers.get(TestSignatureHelper.HEADER_SIGN) + "deadbeef");

        MockHttpServletRequestBuilder req = post("/api/account/authenticate")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body);
        headers.forEach(req::header);

        mvc.perform(req).andExpect(status().isUnauthorized());
    }

    // ========== #59：authenticate-by-code（验证码登录）==========

    @Test
    void given_valid_signature_and_correct_email_code_when_authenticate_by_code_then_returns_subject_view()
        throws Exception {
        String email = "authcode-sig@example.com";
        Long userId = createEmailAccount(email, PASSWORD);
        String code = sendEmailCode(email, "LOGIN");

        String body = "{\"identifier\":\"" + email + "\",\"code\":\"" + code + "\"}";

        mvc.perform(signedPost("/api/account/authenticate-by-code", body))
            .andExpect(ApiTestAssertions.assertOk())
            .andExpect(jsonPath("$.data.userId").value(userId))
            .andExpect(jsonPath("$.data.email").value(email))
            .andExpect(jsonPath("$.data.status").value("USABLE"));
    }

    @Test
    void given_valid_signature_and_correct_phone_code_when_authenticate_by_code_then_returns_subject_view()
        throws Exception {
        String phone = "13900150011";
        Long userId = createPhoneAccount(phone, PASSWORD);
        String code = sendSmsCode(phone, "LOGIN");

        String body = "{\"identifier\":\"" + phone + "\",\"code\":\"" + code + "\"}";

        mvc.perform(signedPost("/api/account/authenticate-by-code", body))
            .andExpect(ApiTestAssertions.assertOk())
            .andExpect(jsonPath("$.data.userId").value(userId))
            .andExpect(jsonPath("$.data.phone").value(phone));
    }

    @Test
    void given_valid_signature_but_wrong_code_when_authenticate_by_code_then_returns_api_response_error()
        throws Exception {
        String email = "authcode-wrong@example.com";
        createEmailAccount(email, PASSWORD);
        sendEmailCode(email, "LOGIN");

        String body = "{\"identifier\":\"" + email + "\",\"code\":\"000000\"}";

        // 码错 → 标准 ApiResponse 错误（$.code=400），非 OIDC {error}
        mvc.perform(signedPost("/api/account/authenticate-by-code", body))
            .andExpect(status().isBadRequest())
            .andExpect(ApiTestAssertions.assertError(400))
            .andExpect(jsonPath("$.error").doesNotExist());
    }

    @Test
    void given_tampered_signature_when_authenticate_by_code_then_returns_401() throws Exception {
        String body = "{\"identifier\":\"authcode-gate@example.com\",\"code\":\"123456\"}";

        Map<String, String> headers = signer.sign(body);
        headers.put(TestSignatureHelper.HEADER_SIGN, headers.get(TestSignatureHelper.HEADER_SIGN) + "deadbeef");

        MockHttpServletRequestBuilder req = post("/api/account/authenticate-by-code")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body);
        headers.forEach(req::header);

        // 类级 @RequireSignature gate：篡改签名 → 401（AC「各端点错签名 → 401」）
        mvc.perform(req).andExpect(status().isUnauthorized());
    }

    // ========== #59：register（注册，密码可选）==========

    @Test
    void given_valid_signature_and_email_with_code_when_register_then_creates_account() throws Exception {
        String email = "register-sig@example.com";
        String code = sendEmailCode(email, "REGISTER");

        String body = "{\"email\":\"" + email + "\",\"code\":\"" + code + "\",\"password\":\"" + PASSWORD + "\"}";

        mvc.perform(signedPost("/api/account/register", body))
            .andExpect(ApiTestAssertions.assertOk())
            .andExpect(jsonPath("$.data.userId").isString())
            .andExpect(jsonPath("$.data.email").value(email))
            .andExpect(jsonPath("$.data.status").value("USABLE"));

        assertThat(accountRepository.findByEmail(email)).isPresent();
    }

    @Test
    void given_valid_signature_and_phone_with_code_when_register_then_creates_account() throws Exception {
        String phone = "13900150022";
        String code = sendSmsCode(phone, "REGISTER");

        String body = "{\"phone\":\"" + phone + "\",\"code\":\"" + code + "\",\"password\":\"" + PASSWORD + "\"}";

        mvc.perform(signedPost("/api/account/register", body))
            .andExpect(ApiTestAssertions.assertOk())
            .andExpect(jsonPath("$.data.phone").value(phone));

        assertThat(accountRepository.findByPhone(phone)).isPresent();
    }

    @Test
    void given_valid_signature_and_email_with_code_but_no_password_when_register_then_creates_passwordless_account()
        throws Exception {
        String email = "register-nopass@example.com";
        String code = sendEmailCode(email, "REGISTER");

        String body = "{\"email\":\"" + email + "\",\"code\":\"" + code + "\"}";

        mvc.perform(signedPost("/api/account/register", body))
            .andExpect(ApiTestAssertions.assertOk())
            .andExpect(jsonPath("$.data.email").value(email));

        Account account = accountRepository.findByEmail(email).orElseThrow();
        assertThat(account.getPasswordHash()).isNull();
    }

    @Test
    void given_valid_signature_but_wrong_code_when_register_then_returns_error_and_no_account() throws Exception {
        String email = "register-wrongcode@example.com";
        sendEmailCode(email, "REGISTER");

        String body = "{\"email\":\"" + email + "\",\"code\":\"000000\",\"password\":\"" + PASSWORD + "\"}";

        // 验不过码不建号（防占用他人联络方式）
        mvc.perform(signedPost("/api/account/register", body))
            .andExpect(status().isBadRequest())
            .andExpect(ApiTestAssertions.assertError(400));

        assertThat(accountRepository.findByEmail(email)).isEmpty();
    }

    @Test
    void given_valid_signature_and_existing_email_with_code_when_register_then_returns_409() throws Exception {
        String email = "register-dup@example.com";
        createEmailAccount(email, PASSWORD);
        String code = sendEmailCode(email, "REGISTER");

        String body = "{\"email\":\"" + email + "\",\"code\":\"" + code + "\",\"password\":\"" + PASSWORD + "\"}";

        // 验码通过后才到唯一性检查 → 409 EMAIL_ALREADY_EXISTS
        mvc.perform(signedPost("/api/account/register", body))
            .andExpect(status().isConflict())
            .andExpect(ApiTestAssertions.assertError(409));
    }

    @Test
    void given_tampered_signature_when_register_then_returns_401_and_no_account() throws Exception {
        String email = "sig-gate@example.com";
        String body = "{\"email\":\"" + email + "\",\"code\":\"123456\",\"password\":\"" + PASSWORD + "\"}";

        Map<String, String> headers = signer.sign(body);
        headers.put(TestSignatureHelper.HEADER_SIGN, headers.get(TestSignatureHelper.HEADER_SIGN) + "deadbeef");

        MockHttpServletRequestBuilder req = post("/api/account/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body);
        headers.forEach(req::header);

        // 类级 @RequireSignature gate 对新端点同样生效（篡改签名 → 401，不建号）
        mvc.perform(req).andExpect(status().isUnauthorized());
        assertThat(accountRepository.findByEmail(email)).isEmpty();
    }

    // ========== #59：reset-password（重置密码）==========

    @Test
    void given_valid_signature_and_correct_code_when_reset_password_then_204_and_new_password_works()
        throws Exception {
        String email = "reset-sig@example.com";
        createEmailAccount(email, PASSWORD);
        String code = sendEmailCode(email, "RESET_PASSWORD");
        String newPassword = "NewPass5678";

        String body = "{\"identifier\":\"" + email + "\",\"code\":\"" + code + "\","
            + "\"newPassword\":\"" + newPassword + "\"}";

        mvc.perform(signedPost("/api/account/reset-password", body))
            .andExpect(status().isNoContent());

        // 重置生效：新密码能验密登录、旧密码不行
        mvc.perform(signedPost("/api/account/authenticate",
            "{\"identifier\":\"" + email + "\",\"password\":\"" + newPassword + "\"}"))
            .andExpect(ApiTestAssertions.assertOk())
            .andExpect(jsonPath("$.data.email").value(email));
    }

    @Test
    void given_valid_signature_but_wrong_code_when_reset_password_then_returns_error() throws Exception {
        String email = "reset-wrongcode@example.com";
        createEmailAccount(email, PASSWORD);
        sendEmailCode(email, "RESET_PASSWORD");

        String body = "{\"identifier\":\"" + email + "\",\"code\":\"000000\",\"newPassword\":\"NewPass5678\"}";

        mvc.perform(signedPost("/api/account/reset-password", body))
            .andExpect(status().isBadRequest())
            .andExpect(ApiTestAssertions.assertError(400));
    }

    @Test
    void given_tampered_signature_when_reset_password_then_returns_401() throws Exception {
        String email = "reset-gate@example.com";
        createEmailAccount(email, PASSWORD);
        String body = "{\"identifier\":\"" + email + "\",\"code\":\"123456\",\"newPassword\":\"NewPass5678\"}";

        Map<String, String> headers = signer.sign(body);
        headers.put(TestSignatureHelper.HEADER_SIGN, headers.get(TestSignatureHelper.HEADER_SIGN) + "deadbeef");

        MockHttpServletRequestBuilder req = post("/api/account/reset-password")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body);
        headers.forEach(req::header);

        // 类级 @RequireSignature gate：篡改签名 → 401，密码未被改（AC「各端点错签名 → 401」）
        mvc.perform(req).andExpect(status().isUnauthorized());
        // 原密码仍可用——确认 gate 在 controller 前挡下、未发生部分写
        mvc.perform(signedPost("/api/account/authenticate",
            "{\"identifier\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
            .andExpect(ApiTestAssertions.assertOk());
    }
}
