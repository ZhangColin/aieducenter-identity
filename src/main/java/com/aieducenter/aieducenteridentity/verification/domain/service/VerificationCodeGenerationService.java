package com.aieducenter.aieducenteridentity.verification.domain.service;

import java.security.SecureRandom;

import com.cartisan.core.stereotype.DomainService;

/**
 * 验证码生成领域服务。
 *
 * <h3>规则</h3>
 * <ul>
 *   <li>生成 6 位随机数字</li>
 *   <li>范围：100000-999999</li>
 * </ul>
 *
 * @since 0.1.0
 */
@DomainService
public class VerificationCodeGenerationService {

    private static final int MIN_CODE = 100000;
    private static final int MAX_CODE = 999999;
    private final SecureRandom random;

    public VerificationCodeGenerationService() {
        this.random = new SecureRandom();
    }

    /**
     * 生成验证码。
     *
     * @return 6 位随机数字
     */
    public String generate() {
        int code = MIN_CODE + random.nextInt(MAX_CODE - MIN_CODE + 1);
        return String.format("%06d", code);
    }
}
