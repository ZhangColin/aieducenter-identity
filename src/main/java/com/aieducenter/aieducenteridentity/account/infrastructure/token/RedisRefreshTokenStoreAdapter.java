package com.aieducenter.aieducenteridentity.account.infrastructure.token;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import com.aieducenter.aieducenteridentity.account.domain.token.RefreshTokenStore;
import com.cartisan.core.stereotype.Adapter;
import com.cartisan.core.stereotype.PortType;

/**
 * refresh_token 的 Redis 存储——不透明串 {@code refresh:{token}→userId}（ADR-0002 / issue #13）。
 *
 * <p>{@link #consume} 用 Lua 脚本做原子 GETDEL——单次使用、轮换防重放（并发两次 /refresh 同一 token 只有一次命中）。</p>
 *
 * @since 0.1.0
 */
@Adapter(PortType.CLIENT)
@Component
public class RedisRefreshTokenStoreAdapter implements RefreshTokenStore {

    private static final String KEY_PREFIX = "refresh:";

    /** Lua：原子返回并删除（GETDEL 语义）。命中返回 userId 字符串，否则 nil。 */
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

    public RedisRefreshTokenStoreAdapter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void save(String refreshToken, Long userId, Duration ttl) {
        redisTemplate.opsForValue().set(KEY_PREFIX + refreshToken, String.valueOf(userId), ttl);
    }

    @Override
    public Optional<Long> consume(String refreshToken) {
        String userId = redisTemplate.execute(CONSUME, List.of(KEY_PREFIX + refreshToken));
        if (userId == null || userId.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(Long.parseLong(userId));
    }
}
