package com.aieducenter.aieducenteridentity.verification.application.dto;

/**
 * 校验短信验证码命令。
 *
 * @param phone 手机号
 * @param code 验证码
 * @param purpose 验证码目的（REGISTER/RESET_PASSWORD/LOGIN）
 */
public record VerifySmsCodeCommand(
    String phone,
    String code,
    String purpose
) {}
