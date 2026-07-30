package com.aieducenter.aieducenteridentity.verification.infrastructure.redis;

import java.util.concurrent.TimeUnit;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import com.aieducenter.aieducenteridentity.verification.domain.repository.CaptchaRepository;
import com.cartisan.core.stereotype.Adapter;
import com.cartisan.core.stereotype.PortType;

/**
 * 图形验证码 Redis 存储适配器。
 */
@Adapter(PortType.REPOSITORY)
@Repository
public class RedisCaptchaRepository implements CaptchaRepository {

    private static final String KEY_PREFIX = "captcha:";
    private static final long EXPIRE_SECONDS = 180; // 3 分钟

    private final RedisTemplate<String, String> redisTemplate;

    public RedisCaptchaRepository(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void save(String captchaId, String code, long expireSeconds) {
        String key = KEY_PREFIX + captchaId;
        redisTemplate.opsForValue().set(key, code, expireSeconds, TimeUnit.SECONDS);
    }

    @Override
    public boolean verifyAndDelete(String captchaId, String code) {
        String key = KEY_PREFIX + captchaId;
        String storedCode = redisTemplate.opsForValue().get(key);

        if (storedCode != null && storedCode.equalsIgnoreCase(code)) {
            redisTemplate.delete(key);
            return true;
        }
        return false;
    }
}
