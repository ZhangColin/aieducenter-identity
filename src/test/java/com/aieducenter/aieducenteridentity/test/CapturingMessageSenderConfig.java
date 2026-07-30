package com.aieducenter.aieducenteridentity.test;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import com.aieducenter.aieducenteridentity.verification.domain.service.MessageSender;

/**
 * 用 {@link CapturingMessageSender} 覆盖生产的日志桩，作为测试环境唯一的 {@link MessageSender}。
 *
 * <p>{@code @Primary} 确保在存在 {@code LogMessageSenderAdapter}（生产日志桩）时，
 * {@link com.aieducenter.aieducenteridentity.verification.application.VerificationCodeAppService}
 * 注入到捕获实现。
 */
@TestConfiguration(proxyBeanMethods = false)
public class CapturingMessageSenderConfig {

    @Bean
    @Primary
    CapturingMessageSender capturingMessageSender() {
        return new CapturingMessageSender();
    }
}
