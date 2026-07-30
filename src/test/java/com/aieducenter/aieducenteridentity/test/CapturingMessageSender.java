package com.aieducenter.aieducenteridentity.test;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.aieducenter.aieducenteridentity.verification.domain.enums.VerificationPurpose;
import com.aieducenter.aieducenteridentity.verification.domain.service.MessageSender;

/**
 * 测试用消息发送器：内存捕获"实际下发了哪个验证码"。
 *
 * <p>替代生产的 {@code LogMessageSenderAdapter}（日志桩），让集成测试能断言发送端真实收到的码，
 * 覆盖 bug#3 回归（短信码不再硬编码 123456，而是生成器产出的真实码）。
 */
public class CapturingMessageSender implements MessageSender {

    private final Map<String, String> lastCodeByTarget = new ConcurrentHashMap<>();

    @Override
    public void send(String target, String code, VerificationPurpose purpose) {
        lastCodeByTarget.put(target, code);
    }

    /**
     * 返回某联络方式最近一次"发送"的验证码。
     */
    public String lastCodeFor(String target) {
        return lastCodeByTarget.get(target);
    }

    /**
     * 清空捕获记录（每个测试前重置）。
     */
    public void reset() {
        lastCodeByTarget.clear();
    }
}
