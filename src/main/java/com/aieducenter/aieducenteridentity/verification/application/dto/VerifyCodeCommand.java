package com.aieducenter.aieducenteridentity.verification.application.dto;

/**
 * 校验验证码命令。
 *
 * @param email 邮箱地址
 * @param code 验证码
 * @param purpose 验证码目的（REGISTER/RESET_PASSWORD）
 */
public record VerifyCodeCommand(
    String email,
    String code,
    String purpose
) {}
