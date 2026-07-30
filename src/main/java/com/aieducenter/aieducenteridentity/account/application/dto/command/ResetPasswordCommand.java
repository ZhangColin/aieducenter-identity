package com.aieducenter.aieducenteridentity.account.application.dto.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;

/**
 * 重置密码命令（经手机/邮箱验证码，无需旧密码）。
 *
 * @param account          邮箱或手机号
 * @param verificationCode 验证码
 * @param newPassword      新明文密码
 */
public record ResetPasswordCommand(
    @NotBlank String account,
    @NotBlank String verificationCode,
    @NotBlank
    @Size(min = 8, max = 20)
    @Pattern(regexp = "^(?=.*[a-zA-Z])(?=.*\\d).+$")
    String newPassword
) {}
