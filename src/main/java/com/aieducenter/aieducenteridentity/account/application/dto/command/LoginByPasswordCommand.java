package com.aieducenter.aieducenteridentity.account.application.dto.command;

import jakarta.validation.constraints.NotBlank;

/**
 * 密码登录命令。
 *
 * @param account     邮箱或手机号（登录定位）
 * @param password    明文密码
 * @param captchaId   图形验证码 ID
 * @param captchaCode 图形验证码
 */
public record LoginByPasswordCommand(
    @NotBlank String account,
    @NotBlank String password,
    @NotBlank String captchaId,
    @NotBlank String captchaCode
) {}
