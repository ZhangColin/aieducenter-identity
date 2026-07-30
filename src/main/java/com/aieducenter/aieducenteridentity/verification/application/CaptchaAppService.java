package com.aieducenter.aieducenteridentity.verification.application;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.aieducenter.aieducenteridentity.verification.application.dto.CreateCaptchaResponse;
import com.aieducenter.aieducenteridentity.verification.domain.error.CaptchaError;
import com.aieducenter.aieducenteridentity.verification.domain.repository.CaptchaRepository;
import com.aieducenter.aieducenteridentity.verification.domain.service.CaptchaGenerationService;
import com.cartisan.core.exception.DomainException;
import com.cartisan.core.util.Assertions;

/**
 * 图形验证码应用服务。
 */
@Service
public class CaptchaAppService {

    private final CaptchaGenerationService generator;
    private final CaptchaRepository repository;

    public CaptchaAppService(
            CaptchaGenerationService generator,
            CaptchaRepository repository) {
        this.generator = generator;
        this.repository = repository;
    }

    /**
     * 创建图形验证码。
     *
     * @return 验证码响应（图片和 ID）
     */
    public CreateCaptchaResponse createCaptcha() {
        var result = generator.generate();

        String captchaId = UUID.randomUUID().toString();

        // 保存到 Redis，3 分钟过期
        repository.save(captchaId, result.code(), 180);

        return new CreateCaptchaResponse(result.image(), captchaId);
    }

    /**
     * 校验图形验证码。
     *
     * @param captchaId 验证码 ID
     * @param code 用户输入的验证码
     * @throws DomainException 验证码错误或已过期
     */
    public void verifyCaptcha(String captchaId, String code) {
        boolean verified = repository.verifyAndDelete(captchaId, code);
        Assertions.require(verified, CaptchaError.CAPTCHA_INVALID);
    }
}
