package com.aieducenter.aieducenteridentity.account.endpoints.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.aieducenter.aieducenteridentity.test.IdentityIntegrationTestBase;
import com.aieducenter.aieducenteridentity.test.TestSignatureHelper;
import com.aieducenter.aieducenteridentity.test.WireMockAppRegistryConfig;
import com.cartisan.test.base.ApiTestAssertions;

/**
 * 签名服务 tracer bullet 集成测试（#58）：{@code POST /api/account/authenticate} 全链路——
 * 签名 gate（{@code @RequireSignature} → {@code SignatureVerificationFilter} / 拦截器 →
 * {@code RemoteApiKeyProvider} 解析 WireMock 预置的 api-keys stub）→ controller 委托
 * {@code AccountAuthAppService.authenticate} → {@code SubjectView} 响应序列化。
 *
 * <p>薄壳 adapter 不重测领域逻辑（验密/防枚举/状态机已在 AppService 层覆盖）——只验外部 HTTP 行为：
 * 签名放行、委托、响应格式、错误响应为标准 {@code ApiResponse}（非 OIDC {@code {error}}）、
 * 缺失 / 错签名返 401。{@link TestSignatureHelper} 构造合法签名，凭据取自
 * {@link WireMockAppRegistryConfig} 的 api-keys stub。</p>
 */
@Transactional
class SignedAccountControllerIntegrationTest extends IdentityIntegrationTestBase {

    private static final String PASSWORD = "Pass1234";

    /** 可信调用方签名工具——凭据与 WireMockAppRegistryConfig 的 api-keys stub 同源。 */
    private final TestSignatureHelper signer = new TestSignatureHelper(
        WireMockAppRegistryConfig.SIGNED_CALLER_API_KEY,
        WireMockAppRegistryConfig.SIGNED_CALLER_API_SECRET);

    /** 带 5 个合法签名头 POST JSON。 */
    private MockHttpServletRequestBuilder signedPost(String path, String body) {
        MockHttpServletRequestBuilder req = post(path)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body);
        signer.sign(body).forEach(req::header);
        return req;
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
}
