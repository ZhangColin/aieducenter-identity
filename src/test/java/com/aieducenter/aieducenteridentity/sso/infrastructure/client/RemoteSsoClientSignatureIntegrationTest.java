package com.aieducenter.aieducenteridentity.sso.infrastructure.client;

import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import com.aieducenter.aieducenteridentity.sso.domain.session.SsoSession;
import com.aieducenter.aieducenteridentity.test.IdentityIntegrationTestBase;
import com.github.tomakehurst.wiremock.WireMockServer;

import jakarta.servlet.http.Cookie;

/**
 * 服务间签名集成测试（issue #31 AC#1）：identity 解析 client 时，出站调 app-registry bootstrap
 * 带 cartisan-openapi 签名头——WireMock 捕获请求验签名头存在（不验签名值，值由框架单测 + 真实 app-registry 兜）。
 *
 * <p>复用 {@link IdentityIntegrationTestBase} 的 demo 消费方 + WireMock stub app-registry（{@code WireMockAppRegistryConfig}）。
 * 凭 {@code /authorize}（带 SSO cookie）触发一次 client 解析 → OpenApiClient 签名出站 → WireMock journal 留痕。</p>
 */
@Transactional
class RemoteSsoClientSignatureIntegrationTest extends IdentityIntegrationTestBase {

    private static final String BOOTSTRAP_PATH = RemoteSsoClientRepositoryAdapter.bootstrapPath(CLIENT_ID);

    @Autowired
    private WireMockServer appRegistryWireMock;

    @Test
    void given_resolve_client_when_call_app_registry_then_signature_headers_present() throws Exception {
        Long userId = createEmailAccount("signature-test@aieducenter.com", "Password123");
        SsoSession session = createSsoSession(userId, "用户");
        Cookie cookie = ssoCookie(session.sessionId());

        // /authorize（凭 SSO cookie 免登发 code）触发 client 解析 → OpenApiClient 签名出站调 bootstrap。
        mvc.perform(get("/authorize")
                .param("client_id", CLIENT_ID).param("redirect_uri", REDIRECT_URI)
                .param("state", "s1").param("nonce", "n1").cookie(cookie))
            .andExpect(status().isFound());

        // AC#1：bootstrap 请求带 5 个签名头（存在即可，不验值）。
        appRegistryWireMock.verify(getRequestedFor(urlEqualTo(BOOTSTRAP_PATH))
            .withHeader("X-Api-Key", matching(".+"))
            .withHeader("X-Timestamp", matching(".+"))
            .withHeader("X-Nonce", matching(".+"))
            .withHeader("X-Body-Digest", matching(".+"))
            .withHeader("X-Sign", matching(".+")));
    }
}
