package com.aieducenter.aieducenteridentity.sso.endpoints.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

import com.aieducenter.aieducenteridentity.account.infrastructure.token.JwtTokenProperties;
import com.aieducenter.aieducenteridentity.sso.endpoints.SsoIntegrationTestBase;

/**
 * {@code GET /.well-known/openid-configuration} HTTP 黑盒集成测试（issue #17 AC）。
 *
 * <p>覆盖：issuer（== token 的 iss）+ authorize/token/userinfo/jwks 端点 + scopes_supported +
 * id_token_signing_alg_values_supported(RS256) + response/grant types。</p>
 */
class DiscoveryControllerIntegrationTest extends SsoIntegrationTestBase {

    @Autowired
    private JwtTokenProperties jwtTokenProperties;

    @Test
    void get_discovery_then_full_oidc_config_with_consistent_issuer() throws Exception {
        String issuer = jwtTokenProperties.getIssuer();

        MvcResult result = mvc.perform(get("/.well-known/openid-configuration"))
            .andExpect(status().isOk())
            // issuer 必须与 token 的 iss 一致（OIDC 自发现契约）
            .andExpect(jsonPath("$.issuer").value(issuer))
            .andExpect(jsonPath("$.authorization_endpoint").value(issuer + "/authorize"))
            .andExpect(jsonPath("$.token_endpoint").value(issuer + "/token"))
            .andExpect(jsonPath("$.userinfo_endpoint").value(issuer + "/userinfo"))
            .andExpect(jsonPath("$.jwks_uri").value(issuer + "/jwks"))
            .andExpect(jsonPath("$.response_types_supported[0]").value("code"))
            .andExpect(jsonPath("$.grant_types_supported", org.hamcrest.Matchers.hasItems(
                "authorization_code", "refresh_token")))
            .andExpect(jsonPath("$.subject_types_supported[0]").value("public"))
            .andExpect(jsonPath("$.id_token_signing_alg_values_supported[0]").value("RS256"))
            .andExpect(jsonPath("$.token_endpoint_auth_methods_supported[0]").value("client_secret_post"))
            .andReturn();

        // scopes_supported 含 OIDC 标准 scope
        String body = result.getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(
            com.jayway.jsonpath.JsonPath.read(body, "$.scopes_supported").toString())
            .contains("openid", "profile", "email", "phone");
    }
}
