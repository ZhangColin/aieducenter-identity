package com.aieducenter.aieducenteridentity.sso.endpoints;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import com.aieducenter.aieducenteridentity.sso.domain.session.SsoSession;
import com.aieducenter.aieducenteridentity.sso.infrastructure.client.RemoteSsoClientRepositoryAdapter;
import com.aieducenter.aieducenteridentity.sso.infrastructure.client.SsoClientInfo;
import com.cartisan.web.response.ApiResponse;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;

import jakarta.servlet.http.Cookie;

/**
 * SSO client 本地缓存 + 抖动降级集成测试（issue #30 AC#3 / #32 AC#1 AC#2）：唯一 seam = MockMvc 黑盒，
 * app-registry 由 WireMock stub（{@link com.aieducenter.aieducenteridentity.test.WireMockAppRegistryConfig}）。
 *
 * <p>覆盖：
 * <ul>
 *   <li>同一 client 两次 /authorize → bootstrap stub 仅命中一次（fresh 缓存，#30）；</li>
 *   <li>{@code active=false} 的 client 被拒办，且二次 /authorize 不再命中 WireMock（负面缓存，#32 AC#1）；</li>
 *   <li>app-registry 返 404「查不到」→ 走负面缓存、/authorize 拒办 {@code unauthorized_client}(400)（稳定结论，非抖动，#42）；</li>
 *   <li>app-registry 抖动（500）+ 有过期缓存 → 过期兜底、/authorize 仍正常发 code（不拒办、不抛 500，#32 AC#2）；</li>
 *   <li>app-registry 抖动（500）+ 无缓存 → 拒办 {@code temporarily_unavailable}(503)（infra 故障 ≠ client 配置错，#42 / ADR-0006）。</li>
 * </ul>
 * 命中计数用 WireMock 请求 journal 的 before/after delta（journal 跨测试累积，故取 delta 而非绝对值）。</p>
 */
@Transactional
class SsoClientCacheIntegrationTest extends SsoIntegrationTestBase {

    private static final String DISABLED_CLIENT_ID = "disabled-client";
    private static final String UNKNOWN_CLIENT_ID = "unknown-client";
    private static final String STALE_CLIENT_ID = "stale-client";
    private static final String FLAKY_CLIENT_ID = "flaky-client";

    @Autowired
    private WireMockServer appRegistryWireMock;

    @Test
    void given_two_authorize_for_same_client_when_resolve_then_app_registry_hit_once() throws Exception {
        Long userId = createEmailAccount("cache-test@aieducenter.com", "Password123");
        SsoSession session = createSsoSession(userId, "用户");
        Cookie cookie = ssoCookie(session.sessionId());

        int servedBefore = bootstrapServedCount(CLIENT_ID);

        // 两次 /authorize 同一 client（凭 SSO cookie 免登发 code，每次都解析 client）
        mvc.perform(get("/authorize")
                .param("client_id", CLIENT_ID).param("redirect_uri", REDIRECT_URI)
                .param("state", "s1").param("nonce", "n1").cookie(cookie))
            .andExpect(status().isFound());
        mvc.perform(get("/authorize")
                .param("client_id", CLIENT_ID).param("redirect_uri", REDIRECT_URI)
                .param("state", "s2").param("nonce", "n2").cookie(cookie))
            .andExpect(status().isFound());

        int servedAfter = bootstrapServedCount(CLIENT_ID);
        assertThat(servedAfter - servedBefore)
            .as("同一 client 两次解析应命中 app-registry 仅一次（Caffeine 缓存生效）")
            .isEqualTo(1);
    }

    @Test
    void given_inactive_client_when_authorize_then_rejected_and_negatively_cached() throws Exception {
        stubClient(DISABLED_CLIENT_ID, false, null);
        int servedBefore = bootstrapServedCount(DISABLED_CLIENT_ID);

        // active=false → 拒办（unauthorized_client 400）
        mvc.perform(get("/authorize")
                .param("client_id", DISABLED_CLIENT_ID).param("redirect_uri", REDIRECT_URI)
                .param("state", "s1"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("unauthorized_client"));
        mvc.perform(get("/authorize")
                .param("client_id", DISABLED_CLIENT_ID).param("redirect_uri", REDIRECT_URI)
                .param("state", "s2"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("unauthorized_client"));

        int servedAfter = bootstrapServedCount(DISABLED_CLIENT_ID);
        assertThat(servedAfter - servedBefore)
            .as("active=false 负面缓存：二次 /authorize 不再命中 app-registry")
            .isEqualTo(1);
    }

    @Test
    void given_stale_cache_and_app_registry_jitter_when_authorize_then_served_from_stale_cache() throws Exception {
        StubMapping okStub = stubClient(STALE_CLIENT_ID, true, "$argon2id$stub");
        Long userId = createEmailAccount("stale-test@aieducenter.com", "Password123");
        Cookie cookie = ssoCookie(createSsoSession(userId, "用户").sessionId());

        // 首次 /authorize：app-registry 200 → 缓存 fresh → 发 code
        mvc.perform(get("/authorize")
                .param("client_id", STALE_CLIENT_ID).param("redirect_uri", REDIRECT_URI)
                .param("state", "s1").param("nonce", "n1").cookie(cookie))
            .andExpect(status().isFound())
            .andExpect(header().string("Location", Matchers.startsWith(REDIRECT_URI + "?")));

        // 让缓存项过期（推进时钟过 fresh 窗口）+ app-registry 抖动（200 stub 换成 500）
        ssoMutableClock.advance(
            Duration.ofMillis(RemoteSsoClientRepositoryAdapter.FRESH_TTL_MILLIS).plusMinutes(1));
        appRegistryWireMock.removeStub(okStub);
        stubClientError(STALE_CLIENT_ID, 500);

        int servedBefore = bootstrapServedCount(STALE_CLIENT_ID);

        // 二次 /authorize：缓存过期 → 打 app-registry（500 抖动）→ 过期兜底 → 仍发 code（不拒办、不抛 500）
        mvc.perform(get("/authorize")
                .param("client_id", STALE_CLIENT_ID).param("redirect_uri", REDIRECT_URI)
                .param("state", "s2").param("nonce", "n2").cookie(cookie))
            .andExpect(status().isFound())
            .andExpect(header().string("Location", Matchers.startsWith(REDIRECT_URI + "?")));

        int servedAfter = bootstrapServedCount(STALE_CLIENT_ID);
        assertThat(servedAfter - servedBefore)
            .as("过期兜底前应先尝试刷新（命中 500）")
            .isEqualTo(1);
    }

    @Test
    void given_app_registry_404_when_authorize_then_unauthorized_client_and_negatively_cached() throws Exception {
        stubClientError(UNKNOWN_CLIENT_ID, 404);
        int servedBefore = bootstrapServedCount(UNKNOWN_CLIENT_ID);

        // 404「查不到」是稳定结论（非抖动）→ 走负面缓存 → unauthorized_client(400)
        mvc.perform(get("/authorize")
                .param("client_id", UNKNOWN_CLIENT_ID).param("redirect_uri", REDIRECT_URI)
                .param("state", "s1"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("unauthorized_client"));
        mvc.perform(get("/authorize")
                .param("client_id", UNKNOWN_CLIENT_ID).param("redirect_uri", REDIRECT_URI)
                .param("state", "s2"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("unauthorized_client"));

        int servedAfter = bootstrapServedCount(UNKNOWN_CLIENT_ID);
        assertThat(servedAfter - servedBefore)
            .as("404 负面缓存：二次 /authorize 不再命中 app-registry")
            .isEqualTo(1);
    }

    @Test
    void given_no_cache_and_app_registry_jitter_when_authorize_then_temporarily_unavailable_503() throws Exception {
        stubClientError(FLAKY_CLIENT_ID, 500);
        int servedBefore = bootstrapServedCount(FLAKY_CLIENT_ID);

        // 无缓存 + 抖动（infra 故障）→ temporarily_unavailable(503)，不再误报 unauthorized_client(400)
        mvc.perform(get("/authorize")
                .param("client_id", FLAKY_CLIENT_ID).param("redirect_uri", REDIRECT_URI)
                .param("state", "s1"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.error").value("temporarily_unavailable"));

        int servedAfter = bootstrapServedCount(FLAKY_CLIENT_ID);
        assertThat(servedAfter - servedBefore)
            .as("无缓存首调应打 app-registry（命中 500）后拒办")
            .isEqualTo(1);
    }

    /** 注册 200 stub 返回预置 SsoClientInfo（redirectUris 白名单含 REDIRECT_URI），返回 mapping 以便后续 removeStub。 */
    private StubMapping stubClient(String clientId, boolean active, String hash) {
        SsoClientInfo info = new SsoClientInfo(clientId, 99L, clientId, hash,
            List.of(REDIRECT_URI), List.of(), Set.of("openid"), Set.of("authorization_code"), active);
        String body;
        try {
            body = objectMapper.writeValueAsString(ApiResponse.ok(info));
        } catch (Exception e) {
            throw new RuntimeException("无法序列化 SsoClientInfo stub 响应体", e);
        }
        return appRegistryWireMock.stubFor(WireMock.get(urlEqualTo(bootstrapPath(clientId)))
            .willReturn(okJson(body)));
    }

    /** 注册错误状态 stub（抖动：5xx/超时由 OpenApiClient 包成异常）。 */
    private void stubClientError(String clientId, int status) {
        appRegistryWireMock.stubFor(WireMock.get(urlEqualTo(bootstrapPath(clientId)))
            .willReturn(aResponse().withStatus(status)));
    }

    private static String bootstrapPath(String clientId) {
        return RemoteSsoClientRepositoryAdapter.bootstrapPath(clientId);
    }

    private int bootstrapServedCount(String clientId) {
        return appRegistryWireMock.findAll(getRequestedFor(urlEqualTo(bootstrapPath(clientId)))).size();
    }
}
