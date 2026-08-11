package com.aieducenter.aieducenteridentity.sso.infrastructure.token;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import com.aieducenter.aieducenteridentity.sso.domain.token.RefreshTokenPayload;
import com.aieducenter.aieducenteridentity.sso.domain.token.RefreshTokenStore;
import com.cartisan.core.stereotype.Adapter;
import com.cartisan.core.stereotype.PortType;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * refresh_token 的 Redis 存储——不透明串 {@code refresh:{token}→{userId,sessionId}(JSON)}（ADR-0002 / issue #13/#19）。
 *
 * <p>绑 SSO sessionId（准 SLO）：登出/踢人清会话后，refresh grant 校验 sessionId 仍存活。
 * {@link #consume} 用 Lua 脚本做原子 GETDEL——单次使用、轮换防重放（并发两次 /refresh 同一 token 只有一次命中）。</p>
 *
 * @since 0.1.0
 */
@Adapter(PortType.CLIENT)
@Component
public class RedisRefreshTokenStoreAdapter implements RefreshTokenStore {

    private static final String KEY_PREFIX = "refresh:";

    /** Lua：原子返回并删除（GETDEL 语义）。命中返回 JSON 字符串，否则 nil。 */
    private static final String CONSUME_SCRIPT = """
        local v = redis.call('GET', KEYS[1])
        if v then
            redis.call('DEL', KEYS[1])
        end
        return v
        """;

    private static final DefaultRedisScript<String> CONSUME =
        new DefaultRedisScript<>(CONSUME_SCRIPT, String.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisRefreshTokenStoreAdapter(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void save(String refreshToken, Long userId, String sessionId, Duration ttl) {
        try {
            String json = objectMapper.writeValueAsString(new RefreshTokenPayload(userId, sessionId));
            redisTemplate.opsForValue().set(KEY_PREFIX + refreshToken, json, ttl);
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new IllegalStateException("序列化 refresh_token 载荷失败", ex);
        }
    }

    @Override
    public Optional<RefreshTokenPayload> consume(String refreshToken) {
        String json = redisTemplate.execute(CONSUME, List.of(KEY_PREFIX + refreshToken));
        if (json == null || json.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(json, RefreshTokenPayload.class));
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new IllegalStateException("反序列化 refresh_token 载荷失败", ex);
        }
    }
}
