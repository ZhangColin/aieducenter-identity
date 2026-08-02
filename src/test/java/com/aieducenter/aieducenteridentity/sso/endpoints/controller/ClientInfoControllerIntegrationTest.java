package com.aieducenter.aieducenteridentity.sso.endpoints.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import com.aieducenter.aieducenteridentity.sso.endpoints.SsoIntegrationTestBase;
import com.fasterxml.jackson.core.type.TypeReference;

/**
 * {@code GET /api/auth/client-info} HTTP 黑盒集成测试（issue #24 AC）。
 *
 * <p>覆盖：无 SSO cookie → 200 返回 {clientId, clientName}（公开端点，登录页「登录到 XXX 应用」用）；
 * client_id 缺失/未知 → 400 unauthorized_client（与 /authorize 重定向前错同一形态）。</p>
 */
class ClientInfoControllerIntegrationTest extends SsoIntegrationTestBase {

    @Test
    void given_no_sso_cookie_when_client_info_then_200_with_client_name() throws Exception {
        mvc.perform(get("/api/auth/client-info").param("client_id", CLIENT_ID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.clientId").value(CLIENT_ID))
            .andExpect(jsonPath("$.clientName").value("Demo 消费方"));
    }

    @Test
    void given_valid_client_when_client_info_then_only_minimal_fields_exposed() throws Exception {
        MvcResult result = mvc.perform(get("/api/auth/client-info").param("client_id", CLIENT_ID))
            .andExpect(status().isOk())
            .andReturn();

        // 最小字段断言：公开端点不得泄露 client_secret/redirectUris/scopes 等登记信息
        Map<String, Object> body = objectMapper.readValue(
            result.getResponse().getContentAsString(), new TypeReference<>() {});
        assertThat(body).containsOnlyKeys("clientId", "clientName");
    }

    @Test
    void given_missing_client_id_when_client_info_then_400_unauthorized_client() throws Exception {
        mvc.perform(get("/api/auth/client-info"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("unauthorized_client"));
    }

    @Test
    void given_unknown_client_when_client_info_then_400_unauthorized_client() throws Exception {
        mvc.perform(get("/api/auth/client-info").param("client_id", "ghost-client"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("unauthorized_client"));
    }
}
