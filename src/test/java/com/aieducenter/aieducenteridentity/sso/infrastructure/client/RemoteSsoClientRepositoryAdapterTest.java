package com.aieducenter.aieducenteridentity.sso.infrastructure.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.ExpectedCount.twice;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.aieducenter.aieducenteridentity.sso.domain.client.SsoClient;
import com.cartisan.web.response.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

/**
 * {@link RemoteSsoClientRepositoryAdapter} 单测（#30）——通过端口 {@code SsoClientRepository} 观察行为，
 * HTTP 用 {@link MockRestServiceServer} 替身。覆盖：成功映射、Caffeine 缓存命中省远程、active=false/hash=null
 * 负面缓存、4xx/5xx/网络抖动返回 empty 且不缓存（CONTEXT「跨项目集成：本地缓存」）。
 */
class RemoteSsoClientRepositoryAdapterTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String BASE_URL = "http://app-registry.test";
    private static final String CLIENT_ID = "demo-client";
    private static final String BOOTSTRAP_URL =
        BASE_URL + RemoteSsoClientRepositoryAdapter.bootstrapPath(CLIENT_ID);

    private MockRestServiceServer server;
    private Cache<String, Optional<SsoClient>> cache;
    private RemoteSsoClientRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        cache = Caffeine.newBuilder().expireAfterWrite(30, TimeUnit.MINUTES).build();
        adapter = new RemoteSsoClientRepositoryAdapter(builder.build(), cache);
    }

    private static String body(SsoClientInfo info) {
        try {
            return JSON.writeValueAsString(ApiResponse.ok(info));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static SsoClientInfo info(String clientId, boolean active, String hash) {
        return new SsoClientInfo(clientId, 7L, "Demo 消费方", hash,
            List.of("https://demo.localhost/auth/callback"), Set.of("openid", "email"),
            Set.of("authorization_code", "refresh_token"), active);
    }

    @Test
    void given_active_client_when_find_then_mapped() {
        server.expect(once(), requestTo(BOOTSTRAP_URL))
            .andRespond(withSuccess(body(info(CLIENT_ID, true, "$argon2id$hash")), MediaType.APPLICATION_JSON));

        Optional<SsoClient> result = adapter.findByClientId(CLIENT_ID);

        server.verify();
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
        server.expect(once(), requestTo(BOOTSTRAP_URL))
            .andRespond(withSuccess(body(info(CLIENT_ID, true, "$argon2id$hash")), MediaType.APPLICATION_JSON));

        Optional<SsoClient> first = adapter.findByClientId(CLIENT_ID);
        Optional<SsoClient> second = adapter.findByClientId(CLIENT_ID);

        server.verify();
        assertThat(first).isPresent();
        assertThat(second).containsSame(first.get());
    }

    @Test
    void given_inactive_client_when_find_then_empty_and_negatively_cached() {
        server.expect(once(), requestTo(BOOTSTRAP_URL))
            .andRespond(withSuccess(body(info(CLIENT_ID, false, null)), MediaType.APPLICATION_JSON));

        assertThat(adapter.findByClientId(CLIENT_ID)).isEmpty();
        assertThat(adapter.findByClientId(CLIENT_ID)).isEmpty(); // 命中负面缓存，不再打远程

        server.verify(); // 仅命中一次
    }

    @Test
    void given_not_found_when_find_then_empty_and_not_cached() {
        server.expect(twice(), requestTo(BOOTSTRAP_URL))
            .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThat(adapter.findByClientId(CLIENT_ID)).isEmpty();
        assertThat(adapter.findByClientId(CLIENT_ID)).isEmpty(); // 不缓存 → 再次打远程

        server.verify(); // 命中两次
    }

    @Test
    void given_server_error_when_find_then_empty_and_not_cached() {
        server.expect(twice(), requestTo(BOOTSTRAP_URL))
            .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThat(adapter.findByClientId(CLIENT_ID)).isEmpty();
        assertThat(adapter.findByClientId(CLIENT_ID)).isEmpty(); // 抖动不缓存 → 再次打远程

        server.verify();
    }

    @Test
    void given_blank_client_id_when_find_then_empty_without_remote() {
        // 不设 expectation：若有任何远程调用，MockRestServiceServer 在请求时即抛「未预期请求」。

        assertThat(adapter.findByClientId(null)).isEmpty();
        assertThat(adapter.findByClientId("  ")).isEmpty();
        assertThat(cache.getIfPresent(CLIENT_ID)).isNull();

        server.verify();
    }
}
