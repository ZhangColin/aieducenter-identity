package com.aieducenter.aieducenteridentity.verification.application.dto;

/**
 * 校验验证码结果。
 *
 * @param verified 是否校验通过
 * @param message 结果消息
 */
public record VerifyCodeResult(
    boolean verified,
    String message
) {}
