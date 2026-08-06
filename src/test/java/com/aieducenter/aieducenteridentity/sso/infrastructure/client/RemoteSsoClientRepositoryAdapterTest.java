package com.aieducenter.aieducenteridentity.sso.infrastructure.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.aieducenter.aieducenteridentity.sso.config.SsoProperties;
import com.aieducenter.aieducenteridentity.sso.domain.client.SsoClient;
import com.aieducenter.aieducenteridentity.test.MutableClock;
import com.cartisan.openapi.client.OpenApiClient;
import com.cartisan.openapi.client.OpenApiClientException;
import com.cartisan.web.response.ApiResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

/**
 * {@link RemoteSsoClientRepositoryAdapter} 单测（#30→#31→#32）——通过端口 {@code SsoClientRepository} 观察行为，
 * HTTP/签名由 mock {@link OpenApiClient} 替身、时钟由 {@link MutableClock} 推进。覆盖（#32 完整韧性）：
 * <ul>
 *   <li>成功映射、fresh 窗口命中缓存省远程；</li>
 *   <li>active=false / 404「查不到」负面缓存（二次不打远程）；</li>
 *   <li>抖动（5xx/网络/超时）+ 无缓存 → 返 empty 拒办、不缓存、下次重试；</li>
 *   <li>抖动 + 有过期缓存 → 兜底返回过期值（== 最近一次成功解析，不返脏数据）；</li>
 *   <li>过期 + 远程恢复 → 刷新成新值（不用 stale）；</li>
 *   <li>过期负面缓存 + 抖动 → 兜底仍拒办（empty）。</li>
 * </ul>
 * 签名头存在性由集成测试（WireMock 捕获）覆盖，不在此单测。
 */
class RemoteSsoClientRepositoryAdapterTest {

    private static final String BASE_URL = "http://app-registry.test";
    private static final String CLIENT_ID = "demo-client";
    private static final String BOOTSTRAP_URL =
        BASE_URL + RemoteSsoClientRepositoryAdapter.bootstrapPath(CLIENT_ID);

    private OpenApiClient openApiClient;
    private MutableClock clock;
    private Cache<String, SsoClientCacheEntry> cache;
    private RemoteSsoClientRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        openApiClient = mock(OpenApiClient.class);
        clock = new MutableClock();
        cache = Caffeine.newBuilder().maximumSize(100).build();
        SsoProperties properties = new SsoProperties();
        properties.getAppRegistry().setBaseUrl(BASE_URL);
        adapter = new RemoteSsoClientRepositoryAdapter(openApiClient, properties, cache, clock);
    }

    private static SsoClientInfo info(String clientId, boolean active, String hash) {
        return new SsoClientInfo(clientId, 7L, "Demo 消费方", hash,
            List.of("https://demo.localhost/auth/callback"), Set.of("openid", "email"),
            Set.of("authorization_code", "refresh_token"), active);
    }

    @SuppressWarnings("unchecked")
    private void stubGet(SsoClientInfo info) {
        doReturn(ApiResponse.ok(info)).when(openApiClient)
            .get(eq(BOOTSTRAP_URL), any(TypeReference.class));
    }

    private void stubNotFound() {
        doThrow(new OpenApiClientException(404, "not found")).when(openApiClient)
            .get(eq(BOOTSTRAP_URL), any(TypeReference.class));
    }

    private void stubServerError() {
        doThrow(new OpenApiClientException(500, "boom")).when(openApiClient)
            .get(eq(BOOTSTRAP_URL), any(TypeReference.class));
    }

    private void stubNetworkFailure() {
        // OpenApiClient 把非 OpenApiClientException（如网络/超时）包成 RuntimeException。
        doThrow(new RuntimeException("connection refused")).when(openApiClient)
            .get(eq(BOOTSTRAP_URL), any(TypeReference.class));
    }

    /** 把时钟推进过 fresh 窗口，使已缓存项变 stale。 */
    private void advancePastFreshWindow() {
        clock.advance(Duration.ofMillis(RemoteSsoClientRepositoryAdapter.FRESH_TTL_MILLIS).plusMinutes(1));
    }

    @Test
    void given_active_client_when_find_then_mapped() {
        stubGet(info(CLIENT_ID, true, "$argon2id$hash"));

        Optional<SsoClient> result = adapter.findByClientId(CLIENT_ID);

        assertThat(result).isPresent();
        SsoClient client = result.get();
        assertThat(client.clientId()).isEqualTo(CLIENT_ID);
        assertThat(client.clientName()).isEqualTo("Demo 消费方");
        assertThat(client.clientSecretHash()).isEqualTo("$argon2id$hash");
        assertThat(client.redirectUris()).containsExactly("https://demo.localhost/auth/callback");
        assertThat(client.scopes()).containsExactlyInAnyOrder("openid", "email");
        assertThat(client.grants()).containsExactlyInAnyOrder("authorization_code", "refresh_token");
        assertThat(client.active()).isTrue();
    }

    @Test
    void given_same_client_twice_when_find_then_remote_hit_once() {
        stubGet(info(CLIENT_ID, true, "$argon2id$hash"));

        Optional<SsoClient> first = adapter.findByClientId(CLIENT_ID);
        Optional<SsoClient> second = adapter.findByClientId(CLIENT_ID);

        assertThat(first).isPresent();
        assertThat(second).containsSame(first.get());
        verify(openApiClient, times(1)).get(eq(BOOTSTRAP_URL), any(TypeReference.class));
    }

    @Test
    void given_inactive_client_when_find_then_empty_and_negatively_cached() {
        stubGet(info(CLIENT_ID, false, null));

        assertThat(adapter.findByClientId(CLIENT_ID)).isEmpty();
        assertThat(adapter.findByClientId(CLIENT_ID)).isEmpty(); // 命中负面缓存，不再打远程

        verify(openApiClient, times(1)).get(eq(BOOTSTRAP_URL), any(TypeReference.class));
    }

    @Test
    void given_not_found_when_find_then_empty_and_negatively_cached() {
        stubNotFound();

        assertThat(adapter.findByClientId(CLIENT_ID)).isEmpty();
        assertThat(adapter.findByClientId(CLIENT_ID)).isEmpty(); // 404「查不到」负面缓存，不再打远程

        verify(openApiClient, times(1)).get(eq(BOOTSTRAP_URL), any(TypeReference.class));
    }

    @Test
    void given_server_error_and_no_cache_when_find_then_empty_and_retries() {
        stubServerError();

        assertThat(adapter.findByClientId(CLIENT_ID)).isEmpty();
        assertThat(adapter.findByClientId(CLIENT_ID)).isEmpty(); // 抖动不缓存 → 再次打远程

        verify(openApiClient, times(2)).get(eq(BOOTSTRAP_URL), any(TypeReference.class));
    }

    @Test
    void given_network_failure_and_no_cache_when_find_then_empty_and_retries() {
        stubNetworkFailure();

        assertThat(adapter.findByClientId(CLIENT_ID)).isEmpty();
        assertThat(adapter.findByClientId(CLIENT_ID)).isEmpty(); // 抖动不缓存 → 再次打远程

        verify(openApiClient, times(2)).get(eq(BOOTSTRAP_URL), any(TypeReference.class));
    }

    @Test
    void given_stale_positive_and_server_error_when_find_then_serve_stale() {
        stubGet(info(CLIENT_ID, true, "$argon2id$hash"));
        Optional<SsoClient> first = adapter.findByClientId(CLIENT_ID);
        assertThat(first).isPresent();

        advancePastFreshWindow(); // fresh → stale
        stubServerError(); // app-registry 抖动

        Optional<SsoClient> second = adapter.findByClientId(CLIENT_ID);

        // 有过期缓存 → 兜底；值即最近一次成功解析（不返脏数据）
        assertThat(second).containsSame(first.get());
        verify(openApiClient, times(2)).get(eq(BOOTSTRAP_URL), any(TypeReference.class)); // 尝试刷新（失败）
    }

    @Test
    void given_stale_positive_and_network_failure_when_find_then_serve_stale() {
        stubGet(info(CLIENT_ID, true, "$argon2id$hash"));
        Optional<SsoClient> first = adapter.findByClientId(CLIENT_ID);
        assertThat(first).isPresent();

        advancePastFreshWindow();
        stubNetworkFailure();

        assertThat(adapter.findByClientId(CLIENT_ID)).containsSame(first.get());
        verify(openApiClient, times(2)).get(eq(BOOTSTRAP_URL), any(TypeReference.class));
    }

    @Test
    void given_stale_positive_and_remote_recovered_when_find_then_refreshed() {
        stubGet(info(CLIENT_ID, true, "$argon2id$old"));
        Optional<SsoClient> first = adapter.findByClientId(CLIENT_ID);
        assertThat(first).isPresent();

        advancePastFreshWindow();
        stubGet(info(CLIENT_ID, true, "$argon2id$new")); // 远程恢复、值已变

        Optional<SsoClient> second = adapter.findByClientId(CLIENT_ID);

        assertThat(second).isPresent();
        assertThat(second.get().clientSecretHash()).isEqualTo("$argon2id$new"); // 刷新成新值，不用 stale
        assertThat(second).isNotSameAs(first);
        verify(openApiClient, times(2)).get(eq(BOOTSTRAP_URL), any(TypeReference.class));
    }

    @Test
    void given_stale_negative_and_jitter_when_find_then_serve_empty() {
        stubNotFound();
        assertThat(adapter.findByClientId(CLIENT_ID)).isEmpty(); // 404 负面缓存

        advancePastFreshWindow();
        stubServerError(); // 抖动

        // 有缓存（负面、已过期）→ 兜底 = 拒办（empty），不抛
        assertThat(adapter.findByClientId(CLIENT_ID)).isEmpty();
        verify(openApiClient, times(2)).get(eq(BOOTSTRAP_URL), any(TypeReference.class));
    }

    @Test
    void given_blank_client_id_when_find_then_empty_without_remote() {
        assertThat(adapter.findByClientId(null)).isEmpty();
        assertThat(adapter.findByClientId("  ")).isEmpty();
        assertThat(cache.getIfPresent(CLIENT_ID)).isNull();

        Mockito.verifyNoInteractions(openApiClient);
        verify(openApiClient, never()).get(any(String.class), any(TypeReference.class));
    }
}
