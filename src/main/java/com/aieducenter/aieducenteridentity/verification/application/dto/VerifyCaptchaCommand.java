package com.aieducenter.aieducenteridentity.verification.application.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 校验图形验证码命令。
 */
public record VerifyCaptchaCommand(
    /**
     * 验证码 ID。
     */
    @NotBlank
    String captchaId,

    /**
     * 用户输入的验证码。
     */
    @NotBlank
    String code
) {}
