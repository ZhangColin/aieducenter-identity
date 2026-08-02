package com.aieducenter.aieducenteridentity.verification.domain.service;

import java.security.SecureRandom;

import com.aieducenter.aieducenteridentity.verification.config.VerificationCodeProperties;
import com.cartisan.core.stereotype.DomainService;

/**
 * 验证码生成领域服务。
 *
 * <h3>规则</h3>
 * <ul>
 *   <li>生成 6 位随机数字</li>
 *   <li>范围：100000-999999</li>
 *   <li>dev 固定码（issue #29）：{@code verification.code.dev-code} 非空时直接返回配置值——
 *   生成处固定（不是验码处旁路），码照常存 Redis、照常比对/一次性消费/过期/限流</li>
 * </ul>
 *
 * @since 0.1.0
 */
@DomainService
public class VerificationCodeGenerationService {

    private static final int MIN_CODE = 100000;
    private static final int MAX_CODE = 999999;
    private final SecureRandom random;
    private final String devCode;

    public VerificationCodeGenerationService(VerificationCodeProperties properties) {
        this.random = new SecureRandom();
        this.devCode = properties.getDevCode();
    }

    /**
     * 生成验证码。
     *
     * @return 6 位随机数字；配置 dev 固定码时恒为固定值
     */
    public String generate() {
        if (hasText(devCode)) {
            return devCode;
        }
        int code = MIN_CODE + random.nextInt(MAX_CODE - MIN_CODE + 1);
        return String.format("%06d", code);
    }

    // domain 层不依赖 Spring（架构守护），hasText 自实现；与 CaptchaGenerationService 同款
    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
