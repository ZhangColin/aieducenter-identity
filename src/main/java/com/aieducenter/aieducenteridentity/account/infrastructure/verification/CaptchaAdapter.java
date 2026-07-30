package com.aieducenter.aieducenteridentity.account.infrastructure.verification;

import org.springframework.stereotype.Component;

import com.aieducenter.aieducenteridentity.verification.application.CaptchaAppService;

/**
 * 图形验证码适配器——适配 verification 上下文的 {@link CaptchaAppService}，实现 {@link CaptchaPort}。
 *
 * @since 0.1.0
 */
@Component
public class CaptchaAdapter implements CaptchaPort {

    private final CaptchaAppService captchaAppService;

    public CaptchaAdapter(CaptchaAppService captchaAppService) {
        this.captchaAppService = captchaAppService;
    }

    @Override
    public void verifyCaptcha(String captchaId, String captchaCode) {
        captchaAppService.verifyCaptcha(captchaId, captchaCode);
    }
}
