package com.aieducenter.aieducenteridentity.verification.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 发送短信验证码命令。
 */
public record SendSmsCodeCommand(
    @NotBlank
    @Pattern(regexp = "^1[3-9]\\d{9}$")
    String phone,

    @NotBlank
    String purpose,

    /**
     * 图形验证码 ID。
     */
    @NotBlank
    String captchaId,

    /**
     * 用户输入的图形验证码。
     */
    @NotBlank
    String captchaCode
) {}
