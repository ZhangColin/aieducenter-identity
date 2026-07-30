package com.aieducenter.aieducenteridentity.verification.infrastructure.redis;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import com.aieducenter.aieducenteridentity.verification.domain.enums.VerificationPurpose;
import com.aieducenter.aieducenteridentity.verification.domain.enums.VerificationType;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import com.cartisan.core.stereotype.Adapter;
import com.cartisan.core.stereotype.PortType;
import com.aieducenter.aieducenteridentity.verification.config.VerificationCodeProperties;
import com.aieducenter.aieducenteridentity.verification.domain.aggregate.VerificationCode;
import com.aieducenter.aieducenteridentity.verification.domain.repository.VerificationCodeRepository;

/**
 * Redis 验证码仓储实现。
 *
 * @since 0.1.0
 */
@Adapter(PortType.REPOSITORY)
@Repository
public class RedisVerificationCodeRepository implements VerificationCodeRepository {

    private static final String CODE_KEY_PREFIX = "verification:";
    private static final String EMAIL_LIMIT_PREFIX = "limit:email:";
    private static final String PHONE_LIMIT_PREFIX = "limit:phone:";
    private static final String IP_LIMIT_PREFIX = "limit:ip:";

    private static final Duration CODE_TTL = Duration.ofSeconds(300);  // 5分钟
    private static final Duration IP_LIMIT_TTL = Duration.ofSeconds(3600);  // 1小时

    private final StringRedisTemplate redisTemplate;
    private final VerificationCodeProperties properties;

    public RedisVerificationCodeRepository(StringRedisTemplate redisTemplate, VerificationCodeProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    @Override
    public void save(VerificationCode code) {
        String key = codeKey(code.getTarget(), code.getPurpose().name());

        // 使用 Hash 存储以支持原子操作
        java.util.Map<String, String> data = new java.util.HashMap<>();
        data.put("code", code.getCode());
        data.put("expireAt", String.valueOf(code.getExpireAt().toEpochMilli()));
        data.put("used", String.valueOf(code.isUsed()));
        data.put("type", code.getType().name());

        redisTemplate.opsForHash().putAll(key, data);
        redisTemplate.expire(key, CODE_TTL);
    }

    @Override
    public Optional<VerificationCode> findById(String id) {
        String key = CODE_KEY_PREFIX + id;
        Object data = redisTemplate.opsForHash().get(key, "code");

        if (data == null) {
            return Optional.empty();
        }

        // 从 Hash 读取所有字段
        String code = (String) redisTemplate.opsForHash().get(key, "code");
        String expireAtStr = (String) redisTemplate.opsForHash().get(key, "expireAt");
        String usedStr = (String) redisTemplate.opsForHash().get(key, "used");

        // 从 ID 解析 target 和 purpose
        String[] parts = id.split(":", 2);
        String target = parts[0];
        String purposeName = parts.length > 1 ? parts[1] : "REGISTER";
        VerificationPurpose purpose = VerificationPurpose.valueOf(purposeName);

        long expireAt = expireAtStr != null ? Long.parseLong(expireAtStr) : 0L;
        boolean used = Boolean.parseBoolean(usedStr);
        VerificationType type = VerificationType.valueOf((String) redisTemplate.opsForHash().get(key, "type"));

        return Optional.of(VerificationCode.restore(
            id,
            type,
            target,
            code,
            Instant.ofEpochMilli(expireAt),
            used,
            purpose
        ));
    }

    @Override
    public boolean tryAcquireEmailLock(String email, String purpose) {
        String key = emailLimitKey(email, purpose);
        // 原子操作：仅当 key 不存在时设置，防止竞态条件
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(key, "1", Duration.ofSeconds(properties.getEmailCooldownSeconds()));
        return Boolean.TRUE.equals(acquired);
    }

    @Override
    public boolean tryAcquirePhoneLock(String phone, String purpose) {
        String key = phoneLimitKey(phone, purpose);
        // 原子操作：仅当 key 不存在时设置，防止竞态条件
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(key, "1", Duration.ofSeconds(properties.getPhoneCooldownSeconds()));
        return Boolean.TRUE.equals(acquired);
    }

    @Override
    public long checkAndIncrementIp(String ip) {
        String key = ipLimitKey(ip);
        // 原子操作：increment 返回增加后的值
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1) {
            // 第一次设置时添加过期时间
            redisTemplate.expire(key, IP_LIMIT_TTL);
        }
        return count != null ? count : 1;
    }

    /**
     * Lua 脚本：原子操作验证码校验并标记为已使用。
     *
     * <p>返回 1 表示验证成功，0 表示验证失败。
     */
    private static final String VERIFY_AND_MARK_SCRIPT = """
        local key = KEYS[1]
        local inputCode = ARGV[1]
        local currentTime = tonumber(ARGV[2])

        local data = redis.call('HGETALL', key)
        if #data == 0 then
            return 0  -- 验证码不存在
        end

        -- data 是一个扁平数组 {field1, value1, field2, value2, ...}
        -- 我们需要找到对应字段的值
        local storedCode = nil
        local expireAt = nil
        local used = false

        for i = 1, #data, 2 do
            local field = data[i]
            local value = data[i + 1]
            if field == 'code' then
                storedCode = value
            elseif field == 'expireAt' then
                expireAt = tonumber(value)
            elseif field == 'used' then
                used = (value == '1' or value == 'true')
            end
        end

        -- 检查验证码是否存在
        if not storedCode then
            return 0  -- 验证码不存在
        end

        -- 检查是否已使用
        if used then
            return 0  -- 已使用
        end

        -- 检查是否过期
        if expireAt and currentTime >= expireAt then
            return 0  -- 已过期
        end

        -- 检查验证码是否匹配
        if storedCode ~= inputCode then
            return 0  -- 验证码错误
        end

        -- 验证通过，标记为已使用
        redis.call('HSET', key, 'used', '1')
        return 1  -- 验证成功
        """;

    @Override
    public boolean verifyAndMarkAsUsed(String id, String inputCode) {
        String key = CODE_KEY_PREFIX + id;
        long currentTime = Instant.now().toEpochMilli();

        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText(VERIFY_AND_MARK_SCRIPT);
        script.setResultType(Long.class);

        Long result = redisTemplate.execute(script, java.util.List.of(key), inputCode, String.valueOf(currentTime));
        return result != null && result == 1L;
    }

    private String codeKey(String target, String purpose) {
        return CODE_KEY_PREFIX + target + ":" + purpose;
    }

    private String emailLimitKey(String email, String purpose) {
        return EMAIL_LIMIT_PREFIX + email + ":" + purpose;
    }

    private String phoneLimitKey(String phone, String purpose) {
        return PHONE_LIMIT_PREFIX + phone + ":" + purpose;
    }

    private String ipLimitKey(String ip) {
        return IP_LIMIT_PREFIX + ip;
    }

}
