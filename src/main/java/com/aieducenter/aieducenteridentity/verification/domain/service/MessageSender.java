package com.aieducenter.aieducenteridentity.verification.domain.service;

import com.aieducenter.aieducenteridentity.verification.domain.enums.VerificationPurpose;
import com.cartisan.core.stereotype.Port;
import com.cartisan.core.stereotype.PortType;

/**
 * 消息发送器接口。
 *
 * <h3>职责</h3>
 * 发送验证码到目标（邮件/短信）。
 *
 * @since 0.1.0
 */
@Port(PortType.CLIENT)
public interface MessageSender {

    /**
     * 发送验证码。
     *
     * @param target 目标（邮箱或手机号）
     * @param code 验证码
     * @param purpose 使用目的
     */
    void send(String target, String code, VerificationPurpose purpose);
}
