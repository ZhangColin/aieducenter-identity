package com.aieducenter.aieducenteridentity.verification.endpoints.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import com.aieducenter.aieducenteridentity.test.IdentityIntegrationTestBase;
import com.aieducenter.aieducenteridentity.test.TestSignatureHelper;
import com.aieducenter.aieducenteridentity.test.WireMockAppRegistryConfig;
import com.cartisan.test.base.ApiTestAssertions;

/**
 * 图形验证码签名服务端点集成测试（#62：{@code POST /api/captchas}）。
 *
 * <p>全链路：签名 gate（{@code @RequireSignature}）→ controller 委托 {@code CaptchaAppService.createCaptcha}
 * → {@code {captchaId, image}} 响应序列化。薄壳 adapter 不重测领域逻辑（图形码生成 / Redis 落地已在
 * {@code CaptchaFlowIntegrationTest} / {@code CaptchaAppServiceTest} 覆盖）——只验签名放行、响应字段、
 * 错签名 401。</p>
 */
@Transactional
class SignedCaptchaControllerIntegrationTest extends IdentityIntegrationTestBase {

    /** 可信调用方签名工具——凭据与 WireMockAppRegistryConfig 的 api-keys stub 同源。 */
    private final TestSignatureHelper signer = new TestSignatureHelper(
        WireMockAppRegistryConfig.SIGNED_CALLER_API_KEY,
        WireMockAppRegistryConfig.SIGNED_CALLER_API_SECRET);

    /** 带 5 个合法签名头 POST（无 body——图形码端点无入参）。 */
    private MockHttpServletRequestBuilder signedPost(String path) {
        MockHttpServletRequestBuilder req = post(path)
            .contentType(MediaType.APPLICATION_JSON);
        signer.sign(null).forEach(req::header);
        return req;
    }

    @Test
    void given_valid_signature_when_create_captcha_then_returns_captcha_id_and_image() throws Exception {
        mvc.perform(signedPost("/api/captchas"))
            .andExpect(ApiTestAssertions.assertOk())
            .andExpect(jsonPath("$.data.captchaId").isNotEmpty())
            .andExpect(jsonPath("$.data.image").isNotEmpty());
    }

    @Test
    void given_tampered_signature_when_create_captcha_then_returns_401() throws Exception {
        Map<String, String> headers = signer.sign(null);
        headers.put(TestSignatureHelper.HEADER_SIGN, headers.get(TestSignatureHelper.HEADER_SIGN) + "deadbeef");

        MockHttpServletRequestBuilder req = post("/api/captchas")
            .contentType(MediaType.APPLICATION_JSON);
        headers.forEach(req::header);

        // 类级 @RequireSignature gate：篡改签名 → 401（AC「各端点错签名 → 401」）
        mvc.perform(req).andExpect(status().isUnauthorized());
    }
}
