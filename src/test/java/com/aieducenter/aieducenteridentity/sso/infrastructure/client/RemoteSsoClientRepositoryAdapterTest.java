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

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.aieducenter.aieducenteridentity.sso.config.SsoProperties;
import com.aieducenter.aieducenteridentity.sso.domain.client.SsoClient;
import com.cartisan.openapi.client.OpenApiClient;
import com.cartisan.openapi.client.OpenApiClientException;
import com.cartisan.web.response.ApiResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

/**
 * {@link RemoteSsoClientRepositoryAdapter} 单测（#30→#31）——通过端口 {@code SsoClientRepository} 观察行为，
 * HTTP/签名由 mock {@link OpenApiClient} 替身（#31 切 OpenApiClient 后替 MockRestServiceServer）。覆盖：
 * 成功映射、Caffeine 缓存命中省远程、active=false/hash=null 负面缓存、4xx/5xx/网络抖动返回 empty 且不缓存
 * （CONTEXT「跨项目集成：本地缓存」）。签名头存在性由集成测试（WireMock 捕获）覆盖，不在此单测。
 */
class RemoteSsoClientRepositoryAdapterTest {

    private static final String BASE_URL = "http://app-registry.test";
    private static final String CLIENT_ID = "demo-client";
    private static final String BOOTSTRAP_URL =
        BASE_URL + RemoteSsoClientRepositoryAdapter.bootstrapPath(CLIENT_ID);

    private OpenApiClient openApiClient;
    private Cache<String, Optional<SsoClient>> cache;
    private RemoteSsoClientRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        openApiClient = mock(OpenApiClient.class);
        cache = Caffeine.newBuilder().expireAfterWrite(30, TimeUnit.MINUTES).build();
        SsoProperties properties = new SsoProperties();
        properties.getAppRegistry().setBaseUrl(BASE_URL);
        adapter = new RemoteSsoClientRepositoryAdapter(openApiClient, properties, cache);
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
    void given_not_found_when_find_then_empty_and_not_cached() {
        doThrow(new OpenApiClientException(404, "not found")).when(openApiClient)
            .get(eq(BOOTSTRAP_URL), any(TypeReference.class));

        assertThat(adapter.findByClientId(CLIENT_ID)).isEmpty();
        assertThat(adapter.findByClientId(CLIENT_ID)).isEmpty(); // 不缓存 → 再次打远程

        verify(openApiClient, times(2)).get(eq(BOOTSTRAP_URL), any(TypeReference.class));
    }

    @Test
    void given_server_error_when_find_then_empty_and_not_cached() {
        doThrow(new OpenApiClientException(500, "boom")).when(openApiClient)
            .get(eq(BOOTSTRAP_URL), any(TypeReference.class));

        assertThat(adapter.findByClientId(CLIENT_ID)).isEmpty();
        assertThat(adapter.findByClientId(CLIENT_ID)).isEmpty(); // 抖动不缓存 → 再次打远程

        verify(openApiClient, times(2)).get(eq(BOOTSTRAP_URL), any(TypeReference.class));
    }

    @Test
    void given_network_failure_when_find_then_empty_and_not_cached() {
        // OpenApiClient 把非 OpenApiClientException（如网络）包成 RuntimeException。
        doThrow(new RuntimeException("connection refused")).when(openApiClient)
            .get(eq(BOOTSTRAP_URL), any(TypeReference.class));

        assertThat(adapter.findByClientId(CLIENT_ID)).isEmpty();
        assertThat(adapter.findByClientId(CLIENT_ID)).isEmpty(); // 抖动不缓存 → 再次打远程

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
