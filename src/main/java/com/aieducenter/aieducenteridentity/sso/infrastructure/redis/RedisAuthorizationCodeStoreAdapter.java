package com.aieducenter.aieducenteridentity.sso.infrastructure.redis;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import com.aieducenter.aieducenteridentity.sso.config.SsoProperties;
import com.aieducenter.aieducenteridentity.sso.domain.code.AuthorizationCodeStore;
import com.aieducenter.aieducenteridentity.sso.domain.code.IssuedAuthorizationCode;
import com.cartisan.core.stereotype.Adapter;
import com.cartisan.core.stereotype.PortType;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 授权码的 Redis 存储——不透明 code {@code sso:code:{code} → 载荷(JSON)}（CONTEXT 安全集 / issue #15）。
 *
 * <p>{@link #consume} 用 Lua 脚本做原子 GETDEL——单次使用、防重放（同 {@code RedisRefreshTokenStoreAdapter}）。
 * TTL = {@code identity.sso.code-ttl-seconds}（默认 60s）。</p>
 *
 * @since 0.1.0
 */
@Adapter(PortType.CLIENT)
@Component
public class RedisAuthorizationCodeStoreAdapter implements AuthorizationCodeStore {

    static final String CODE_KEY_PREFIX = "sso:code:";

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int CODE_BYTES = 32;

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
    private final SsoProperties properties;

    public RedisAuthorizationCodeStoreAdapter(StringRedisTemplate redisTemplate, ObjectMapper objectMapper,
            SsoProperties properties) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public String issue(IssuedAuthorizationCode payload) {
        Objects.requireNonNull(payload);
        String code = newCode();
        redisTemplate.opsForValue().set(CODE_KEY_PREFIX + code, write(payload),
            Duration.ofSeconds(properties.getCodeTtlSeconds()));
        return code;
    }

    @Override
    public Optional<IssuedAuthorizationCode> consume(String code) {
        Objects.requireNonNull(code);
        String json = redisTemplate.execute(CONSUME, List.of(CODE_KEY_PREFIX + code));
        if (json == null || json.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(read(json));
    }

    private String write(IssuedAuthorizationCode payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new IllegalStateException("序列化授权码载荷失败", ex);
        }
    }

    private IssuedAuthorizationCode read(String json) {
        try {
            return objectMapper.readValue(json, IssuedAuthorizationCode.class);
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new IllegalStateException("反序列化授权码载荷失败", ex);
        }
    }

    private static String newCode() {
        byte[] bytes = new byte[CODE_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
