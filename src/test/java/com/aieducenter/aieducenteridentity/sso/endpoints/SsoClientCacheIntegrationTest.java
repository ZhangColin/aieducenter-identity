package com.aieducenter.aieducenteridentity.sso.endpoints;

import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import com.aieducenter.aieducenteridentity.sso.domain.session.SsoSession;
import com.aieducenter.aieducenteridentity.sso.infrastructure.client.RemoteSsoClientRepositoryAdapter;
import com.aieducenter.aieducenteridentity.test.WireMockAppRegistryConfig;
import com.github.tomakehurst.wiremock.WireMockServer;

import jakarta.servlet.http.Cookie;

/**
 * SSO client 本地缓存集成测试（issue #30 AC#3）：同一 demo-client 两次 /authorize，app-registry
 * bootstrap stub 仅命中一次（Caffeine 缓存生效）。
 *
 * <p>消费方解析已迁离 stub 适配器、改为消费 WireMock stub 的 app-registry（{@link WireMockAppRegistryConfig}）。
 * 命中计数用 WireMock 请求 journal 的 before/after delta（journal 跨测试累积，故取 delta 而非 verify 绝对值）。</p>
 */
@Transactional
class SsoClientCacheIntegrationTest extends SsoIntegrationTestBase {

    private static final String BOOTSTRAP_PATH = RemoteSsoClientRepositoryAdapter.bootstrapPath(CLIENT_ID);

    @Autowired
    private WireMockServer appRegistryWireMock;

    @Test
    void given_two_authorize_for_same_client_when_resolve_then_app_registry_hit_once() throws Exception {
        Long userId = createEmailAccount("cache-test@aieducenter.com", "Password123");
        SsoSession session = createSsoSession(userId, "用户");
        Cookie cookie = ssoCookie(session.sessionId());

        int servedBefore = bootstrapServedCount();

        // 两次 /authorize 同一 client（凭 SSO cookie 免登发 code，每次都解析 client）
        mvc.perform(get("/authorize")
                .param("client_id", CLIENT_ID).param("redirect_uri", REDIRECT_URI)
                .param("state", "s1").param("nonce", "n1").cookie(cookie))
            .andExpect(status().isFound());
        mvc.perform(get("/authorize")
                .param("client_id", CLIENT_ID).param("redirect_uri", REDIRECT_URI)
                .param("state", "s2").param("nonce", "n2").cookie(cookie))
            .andExpect(status().isFound());

        int servedAfter = bootstrapServedCount();
        assertThat(servedAfter - servedBefore)
            .as("同一 client 两次解析应命中 app-registry 仅一次（Caffeine 缓存生效）")
            .isEqualTo(1);
    }

    private int bootstrapServedCount() {
        return appRegistryWireMock.findAll(getRequestedFor(urlEqualTo(BOOTSTRAP_PATH))).size();
    }
}
