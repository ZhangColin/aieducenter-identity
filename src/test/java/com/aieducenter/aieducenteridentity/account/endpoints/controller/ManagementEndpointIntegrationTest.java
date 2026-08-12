package com.aieducenter.aieducenteridentity.account.endpoints.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import com.aieducenter.aieducenteridentity.test.IdentityIntegrationTestBase;
import com.aieducenter.aieducenteridentity.test.TestSignatureHelper;
import com.aieducenter.aieducenteridentity.test.WireMockAppRegistryConfig;
import com.cartisan.test.base.ApiTestAssertions;

/**
 * 后台管理 gate + 管理详情端点集成测试（{@code GET /api/account/{userId}/management}，#67）。
 *
 * <p>一条缝贯穿 签名 filter → {@code SignatureVerificationInterceptor}（401 认证）→
 * {@code ManagementCallerInterceptor}（403 白名单）→ controller → {@code AccountManagementAppService}
 * → 聚合 → DB。照搬 {@code SignedAccountControllerIntegrationTest} 的真签名范式（MockMvc +
 * Testcontainers 真 PG/Redis + WireMock app-registry），复用 {@link IdentityIntegrationTestBase}。</p>
 *
 * <p>三入口：admin-console 签名（白名单内）→ 200；普通签名调用方（白名单外）→ 403；未签名 → 401。</p>
 *
 * @since 0.1.0
 */
@Transactional
class ManagementEndpointIntegrationTest extends IdentityIntegrationTestBase {

    private static final String PASSWORD = "Pass1234";

    /** admin-console 管理调用方——appName="admin-console"（白名单内），凭据与 WireMock admin-console stub 同源。 */
    private final TestSignatureHelper adminConsoleSigner = new TestSignatureHelper(
        WireMockAppRegistryConfig.ADMIN_CONSOLE_API_KEY,
        WireMockAppRegistryConfig.ADMIN_CONSOLE_API_SECRET);

    /** 普通签名调用方——appName="签名服务测试调用方"（白名单外），凭据与 WireMock signed-caller stub 同源。 */
    private final TestSignatureHelper signedCallerSigner = new TestSignatureHelper(
        WireMockAppRegistryConfig.SIGNED_CALLER_API_KEY,
        WireMockAppRegistryConfig.SIGNED_CALLER_API_SECRET);

    /** 带 5 个合法签名头 GET（指定 signer）。 */
    private MockHttpServletRequestBuilder signedGet(String path, TestSignatureHelper signer) {
        MockHttpServletRequestBuilder req = get(path);
        signer.sign(null).forEach(req::header);
        return req;
    }

    @Test
    void given_admin_console_signature_when_get_management_detail_then_returns_status_locked_hasPassword_and_profile()
            throws Exception {
        String email = "mgmt-detail@example.com";
        Long userId = createEmailAccount(email, PASSWORD);

        mvc.perform(signedGet("/api/account/" + userId + "/management", adminConsoleSigner))
            .andExpect(ApiTestAssertions.assertOk())
            .andExpect(jsonPath("$.data.userId").value(userId))
            .andExpect(jsonPath("$.data.email").value(email))
            .andExpect(jsonPath("$.data.status").value(1)) // AccountStatus.ACTIVE（BaseEnum → Integer code）
            .andExpect(jsonPath("$.data.locked").value(false))
            .andExpect(jsonPath("$.data.hasPassword").value(true));
    }

    @Test
    void given_non_admin_console_signature_when_get_management_detail_then_returns_403() throws Exception {
        Long userId = createEmailAccount("mgmt-403@example.com", PASSWORD);

        // 签名有效（过 401 认证 gate），但调用方非白名单 → 403
        mvc.perform(signedGet("/api/account/" + userId + "/management", signedCallerSigner))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void given_no_signature_when_get_management_detail_then_returns_401() throws Exception {
        Long userId = createEmailAccount("mgmt-401@example.com", PASSWORD);

        // 无签名头 → 签名 gate 回 401（非白名单 403 之前）
        mvc.perform(get("/api/account/" + userId + "/management"))
            .andExpect(status().isUnauthorized());
    }
}
