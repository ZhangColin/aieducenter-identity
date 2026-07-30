package com.aieducenter.aieducenteridentity.verification.application.dto;

/**
 * 发送邮箱验证码命令。
 *
 * @param email 邮箱地址
 * @param purpose 验证码目的（REGISTER/RESET_PASSWORD）
 */
public record SendEmailCodeCommand(
    String email,
    String purpose
) {}
