package com.aieducenter.aieducenteridentity.account.infrastructure.verification;

import com.cartisan.core.stereotype.Port;
import com.cartisan.core.stereotype.PortType;

/**
 * 图形验证码端口——account 上下文南向调用 verification 上下文的图形验证码校验能力。
 *
 * @since 0.1.0
 */
@Port(PortType.CLIENT)
public interface CaptchaPort {

    /**
     * 校验图形验证码。
     *
     * @param captchaId   验证码 ID
     * @param captchaCode 验证码内容
     * @throws com.cartisan.core.exception.DomainException 验证码错误/过期/已用
     */
    void verifyCaptcha(String captchaId, String captchaCode);
}
