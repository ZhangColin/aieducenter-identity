package com.aieducenter.aieducenteridentity.sso.infrastructure.redis;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.aieducenter.aieducenteridentity.sso.config.SsoProperties;
import com.aieducenter.aieducenteridentity.sso.domain.session.SsoSession;
import com.aieducenter.aieducenteridentity.sso.domain.session.SsoSessionRepository;
import com.aieducenter.aieducenteridentity.test.IdentityIntegrationTestBase;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * {@link RedisSsoSessionRepositoryAdapter} 集成测试——背靠 Testcontainers Redis（issue #15）。
 *
 * <p>覆盖：建会话、findActive（命中/滑动续期/未知）、绝对过期清理、删除、按 userId 踢人。</p>
 */
@SpringBootTest
class RedisSsoSessionRepositoryAdapterTest extends IdentityIntegrationTestBase {

    @Autowired
    private SsoSessionRepository repository;

    @Autowired
    private SsoProperties properties;

    @Test
    void given_create_when_findActive_then_returns_session_and_sets_idle_ttl() {
        SsoSession created = repository.create(200L, "阿福");

        assertThat(created.sessionId()).isNotBlank();
        assertThat(created.userId()).isEqualTo(200L);
        assertThat(created.displayName()).isEqualTo("阿福");

        SsoSession active = repository.findActive(created.sessionId()).orElseThrow();
        assertThat(active.userId()).isEqualTo(200L);
        // key 带闲置 TTL
        Long ttl = redisTemplate.getExpire(SESSION_KEY(created.sessionId()));
        assertThat(ttl).isPositive();
    }

    @Test
    void given_shortened_ttl_when_findActive_then_idle_ttl_slid() {
        SsoSession created = repository.create(201L, null);
        String key = SESSION_KEY(created.sessionId());
        // 人为把闲置 TTL 压到 1s（模拟即将闲置过期）
        redisTemplate.expire(key, Duration.ofSeconds(1));
        assertThat(redisTemplate.getExpire(key)).isBetween(1L, 1L);

        // findActive 命中 → 滑动续期回闲置窗口
        repository.findActive(created.sessionId()).orElseThrow();

        assertThat(redisTemplate.getExpire(key)).isGreaterThan(100L);
    }

    @Test
    void given_unknown_session_when_findActive_then_empty() {
        assertThat(repository.findActive("not-a-real-session")).isEmpty();
    }

    @Test
    void given_absolute_expired_session_when_findActive_then_empty_and_deleted() throws Exception {
        // 直接植入一条已绝对过期的会话（绕过 create 的 now+绝对窗口）
        SsoSession expired = new SsoSession("expired-sess", 202L, null,
            Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-02T00:00:00Z"));
        redisTemplate.opsForValue().set(SESSION_KEY("expired-sess"),
            objectMapper.writeValueAsString(expired), Duration.ofSeconds(properties.getSessionIdleSeconds()));

        assertThat(repository.findActive("expired-sess")).isEmpty();
        // 绝对过期被清理
        assertThat(redisTemplate.hasKey(SESSION_KEY("expired-sess"))).isFalse();
    }

    @Test
    void given_session_when_delete_then_findActive_empty() {
        SsoSession created = repository.create(203L, null);
        repository.delete(created.sessionId());
        assertThat(repository.findActive(created.sessionId())).isEmpty();
    }

    @Test
    void given_multiple_sessions_when_deleteByUserId_then_all_cleared_and_count_returned() {
        SsoSession a = repository.create(204L, null);
        repository.create(204L, null);
        repository.create(205L, null); // 另一用户，不受影响

        int removed = repository.deleteByUserId(204L);

        assertThat(removed).isEqualTo(2);
        assertThat(repository.findActive(a.sessionId())).isEmpty();
        assertThat(redisTemplate.hasKey(SESSION_KEY(a.sessionId()))).isFalse();
        // 用户索引已清
        assertThat(redisTemplate.hasKey(USER_INDEX_KEY(204L))).isFalse();
    }

    private String SESSION_KEY(String sessionId) {
        return RedisSsoSessionRepositoryAdapter.SESSION_KEY_PREFIX + sessionId;
    }

    private String USER_INDEX_KEY(Long userId) {
        return RedisSsoSessionRepositoryAdapter.USER_SESSIONS_KEY_PREFIX + userId;
    }
}
