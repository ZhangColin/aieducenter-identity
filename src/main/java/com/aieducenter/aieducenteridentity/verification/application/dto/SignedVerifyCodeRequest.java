package com.aieducenter.aieducenteridentity.verification.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 签名服务裸验码请求——{@code POST /api/verification-codes/verify}（#62）。
 *
 * <p>裸验码（不绑后续操作）：调用方先验码再自决下一步。与发码端点同形——{@code target}
 * 判别邮箱 / 短信，{@code value} 为邮箱地址或手机号。
 *
 * @param target 渠道（EMAIL/SMS）
 * @param value 邮箱地址（target=EMAIL）或手机号（target=SMS）
 * @param code 验证码
 * @param purpose 用途（REGISTER/LOGIN/RESET_PASSWORD）
 * @since 0.1.0
 */
public record SignedVerifyCodeRequest(
    @NotNull CodeTarget target,
    @NotBlank String value,
    @NotBlank String code,
    @NotBlank String purpose
) {}
