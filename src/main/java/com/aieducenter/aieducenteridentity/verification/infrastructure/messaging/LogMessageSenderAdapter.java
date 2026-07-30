package com.aieducenter.aieducenteridentity.verification.infrastructure.messaging;

import com.aieducenter.aieducenteridentity.verification.domain.enums.VerificationPurpose;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.cartisan.core.stereotype.Adapter;
import com.cartisan.core.stereotype.PortType;
import com.aieducenter.aieducenteridentity.verification.domain.service.MessageSender;

/**
 * 日志消息发送器（生产占位实现）。
 *
 * <h3>职责</h3>
 * <p>将验证码输出到日志，作为真实邮件/短信通道接入前的占位实现。
 * 接收到的是 {@link VerificationCodeGenerationService} 生成的真实验证码（不再硬编码），
 * 待真实网关接入后替换本适配器即可，领域层无需改动。
 *
 * @since 0.1.0
 */
@Adapter(PortType.CLIENT)
@Component("verificationLogMessageSender")
public class LogMessageSenderAdapter implements MessageSender {

    private static final Logger log = LoggerFactory.getLogger(LogMessageSenderAdapter.class);

    @Override
    public void send(String target, String code, VerificationPurpose purpose) {
        log.info("[模拟发送验证码] 目标: {}, 验证码: {}, 目的: {}", target, code, purpose);
    }
}
