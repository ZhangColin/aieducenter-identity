package com.aieducenter.aieducenteridentity.sso.infrastructure.redis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.aieducenter.aieducenteridentity.sso.config.SsoProperties;
import com.aieducenter.aieducenteridentity.sso.domain.code.AuthorizationCodeStore;
import com.aieducenter.aieducenteridentity.sso.domain.code.IssuedAuthorizationCode;
import com.aieducenter.aieducenteridentity.test.IdentityIntegrationTestBase;

/**
 * {@link RedisAuthorizationCodeStoreAdapter} 集成测试——背靠 Testcontainers Redis（issue #15）。
 *
 * <p>覆盖：发码/消费往返、一次性（再消费 empty）、未知 empty、TTL 设置。</p>
 */
@SpringBootTest
class RedisAuthorizationCodeStoreAdapterTest extends IdentityIntegrationTestBase {

    @Autowired
    private AuthorizationCodeStore store;

    @Autowired
    private SsoProperties properties;

    private IssuedAuthorizationCode payload(String clientId, String redirectUri) {
        return new IssuedAuthorizationCode(clientId, redirectUri, 300L, "nonce-xyz", "openid profile", "sess-store");
    }

    @Test
    void given_issue_when_consume_then_payload_round_trips_and_ttl_set() {
        String code = store.issue(payload("demo-app", "https://demo.localhost/auth/callback"));

        assertThat(code).isNotBlank();
        IssuedAuthorizationCode consumed = store.consume(code).orElseThrow();
        assertThat(consumed.clientId()).isEqualTo("demo-app");
        assertThat(consumed.redirectUri()).isEqualTo("https://demo.localhost/auth/callback");
        assertThat(consumed.userId()).isEqualTo(300L);
        assertThat(consumed.nonce()).isEqualTo("nonce-xyz");
        assertThat(consumed.scope()).isEqualTo("openid profile");
        assertThat(consumed.sessionId()).isEqualTo("sess-store");

        // TTL 在 code 有效期窗口内
        Long ttl = redisTemplate.getExpire(RedisAuthorizationCodeStoreAdapter.CODE_KEY_PREFIX + code);
        // 已被 consume 删除 → key 不存在（getExpire 返回 null 或 -2）
        assertThat(ttl == null || ttl < 0).isTrue();
    }

    @Test
    void given_issued_code_key_when_check_ttl_then_within_code_window() {
        String code = store.issue(payload("demo-app", "https://demo.localhost/auth/callback"));
        Long ttl = redisTemplate.getExpire(RedisAuthorizationCodeStoreAdapter.CODE_KEY_PREFIX + code);
        assertThat(ttl).isBetween(1L, properties.getCodeTtlSeconds());
    }

    @Test
    void given_already_consumed_when_consume_again_then_empty() {
        String code = store.issue(payload("demo-app", "https://demo.localhost/auth/callback"));
        store.consume(code);
        Optional<IssuedAuthorizationCode> second = store.consume(code);
        assertThat(second).isEmpty();
    }

    @Test
    void given_unknown_code_when_consume_then_empty() {
        assertThat(store.consume("not-a-real-code")).isEmpty();
    }

    @Test
    void given_expired_code_when_consume_then_empty() throws InterruptedException {
        String code = store.issue(payload("demo-app", "https://demo.localhost/auth/callback"));
        // 把 TTL 缩到 1s（模拟 60s 过期后的状态），等其自然过期
        redisTemplate.expire(RedisAuthorizationCodeStoreAdapter.CODE_KEY_PREFIX + code, java.time.Duration.ofSeconds(1));
        Thread.sleep(1_200L);

        assertThat(store.consume(code)).isEmpty();
    }
}
