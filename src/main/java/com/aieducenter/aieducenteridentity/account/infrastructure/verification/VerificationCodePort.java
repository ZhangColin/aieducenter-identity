package com.aieducenter.aieducenteridentity.account.infrastructure.verification;

import com.cartisan.core.stereotype.Port;
import com.cartisan.core.stereotype.PortType;

/**
 * 验证码端口——account 上下文南向调用 verification 上下文的能力抽象。
 *
 * <p>account 依赖此端口而非 verification 的应用服务，保持上下文间解耦（六边形）。</p>
 *
 * @since 0.1.0
 */
@Port(PortType.CLIENT)
public interface VerificationCodePort {

    /**
     * 校验短信验证码。
     *
     * @param phone   手机号
     * @param code    验证码
     * @param purpose 用途（REGISTER/LOGIN/RESET_PASSWORD）
     * @throws com.cartisan.core.exception.DomainException 验证码错误/过期/已用
     */
    void verifyPhoneCode(String phone, String code, String purpose);

    /**
     * 校验邮箱验证码。
     *
     * @param email   邮箱
     * @param code    验证码
     * @param purpose 用途（REGISTER/LOGIN/RESET_PASSWORD）
     * @throws com.cartisan.core.exception.DomainException 验证码错误/过期/已用
     */
    void verifyCode(String email, String code, String purpose);
}
