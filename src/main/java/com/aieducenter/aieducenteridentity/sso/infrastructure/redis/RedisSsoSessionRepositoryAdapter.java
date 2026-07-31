package com.aieducenter.aieducenteridentity.sso.infrastructure.redis;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.aieducenter.aieducenteridentity.sso.config.SsoProperties;
import com.aieducenter.aieducenteridentity.sso.domain.session.SsoSession;
import com.aieducenter.aieducenteridentity.sso.domain.session.SsoSessionRepository;
import com.cartisan.core.stereotype.Adapter;
import com.cartisan.core.stereotype.PortType;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * SSO 会话的 Redis 存储（ADR-0004 / issue #15）。
 *
 * <p>{@code sso:session:{sessionId} → SsoSession(JSON)}，key TTL = 闲置超时（滑动续期）。
 * {@code sso:user-sessions:{userId} → Set<sessionId>} 索引，供按 userId 踢人（登出/改密/封号）批量清理。</p>
 *
 * <p><b>双超时</b>：闲置由 Redis key TTL 强制（findActive 命中时 EXPIRE 续期）；绝对由存储值
 * {@link SsoSession#absoluteExpiryAt()} 强制（findActive 命中但 isExpired 时删除并返回 empty）。</p>
 *
 * @since 0.1.0
 */
@Adapter(PortType.REPOSITORY)
@Component
public class RedisSsoSessionRepositoryAdapter implements SsoSessionRepository {

    static final String SESSION_KEY_PREFIX = "sso:session:";
    static final String USER_SESSIONS_KEY_PREFIX = "sso:user-sessions:";

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int SESSION_ID_BYTES = 32;

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final SsoProperties properties;

    public RedisSsoSessionRepositoryAdapter(StringRedisTemplate redisTemplate, ObjectMapper objectMapper,
            SsoProperties properties) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public SsoSession create(Long userId, String displayName) {
        String sessionId = newSessionId();
        Instant now = Instant.now();
        SsoSession session = new SsoSession(sessionId, userId, displayName, now,
            now.plusSeconds(properties.getSessionAbsoluteSeconds()));

        String key = SESSION_KEY_PREFIX + sessionId;
        redisTemplate.opsForValue().set(key, write(session),
            Duration.ofSeconds(properties.getSessionIdleSeconds()));
        // 登记 userId 索引（踢人用）；索引 TTL 随绝对窗口兜底自清。
        String indexKey = USER_SESSIONS_KEY_PREFIX + userId;
        redisTemplate.opsForSet().add(indexKey, sessionId);
        redisTemplate.expire(indexKey, Duration.ofSeconds(properties.getSessionAbsoluteSeconds()));
        return session;
    }

    @Override
    public Optional<SsoSession> findActive(String sessionId) {
        Objects.requireNonNull(sessionId);
        String key = SESSION_KEY_PREFIX + sessionId;
        String json = redisTemplate.opsForValue().get(key);
        if (json == null) {
            // key 不存在：闲置过期 / 已登出 / 未知。
            return Optional.empty();
        }
        SsoSession session = read(json);
        if (session.isExpired(Instant.now())) {
            // 绝对过期：清理并视为不存在。
            delete(sessionId, session.userId());
            return Optional.empty();
        }
        // 滑动续闲置超时。
        redisTemplate.expire(key, Duration.ofSeconds(properties.getSessionIdleSeconds()));
        return Optional.of(session);
    }

    @Override
    public void delete(String sessionId) {
        Objects.requireNonNull(sessionId);
        String json = redisTemplate.opsForValue().get(SESSION_KEY_PREFIX + sessionId);
        if (json == null) {
            return;
        }
        SsoSession session = read(json);
        delete(sessionId, session.userId());
    }

    @Override
    public int deleteByUserId(Long userId) {
        Objects.requireNonNull(userId);
        String indexKey = USER_SESSIONS_KEY_PREFIX + userId;
        Set<String> sessionIds = redisTemplate.opsForSet().members(indexKey);
        if (sessionIds == null || sessionIds.isEmpty()) {
            return 0;
        }
        List<String> keys = sessionIds.stream().map(SESSION_KEY_PREFIX::concat).toList();
        redisTemplate.delete(keys);
        redisTemplate.delete(indexKey);
        return sessionIds.size();
    }

    private void delete(String sessionId, Long userId) {
        redisTemplate.delete(SESSION_KEY_PREFIX + sessionId);
        if (userId != null) {
            redisTemplate.opsForSet().remove(USER_SESSIONS_KEY_PREFIX + userId, sessionId);
        }
    }

    private String write(SsoSession session) {
        try {
            return objectMapper.writeValueAsString(session);
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new IllegalStateException("序列化 SSO 会话失败", ex);
        }
    }

    private SsoSession read(String json) {
        try {
            return objectMapper.readValue(json, SsoSession.class);
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new IllegalStateException("反序列化 SSO 会话失败", ex);
        }
    }

    private static String newSessionId() {
        byte[] bytes = new byte[SESSION_ID_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
