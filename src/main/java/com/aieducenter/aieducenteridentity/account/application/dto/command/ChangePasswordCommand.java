package com.aieducenter.aieducenteridentity.account.application.dto.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 修改密码命令（验证旧密码；需登录态）。
 *
 * @param oldPassword 旧明文密码
 * @param newPassword 新明文密码
 */
public record ChangePasswordCommand(
    @NotBlank String oldPassword,
    @NotBlank
    @Size(min = 8, max = 20)
    @Pattern(regexp = "^(?=.*[a-zA-Z])(?=.*\\d).+$")
    String newPassword
) {}
