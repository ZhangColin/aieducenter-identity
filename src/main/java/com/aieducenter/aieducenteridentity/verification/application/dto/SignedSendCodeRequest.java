package com.aieducenter.aieducenteridentity.verification.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 签名服务发码请求——{@code POST /api/verification-codes}（#62）。
 *
 * <p>统一渠道判别 {@code target}（{@link CodeTarget}）+ {@code value}（邮箱地址或手机号），
 * 替代 SSO 浏览器闭环里分 {@code /email}、{@code /sms} 两端点的写法——机机调用方一个端点搞定。
 * 图形码 {@code captchaId/captchaCode} <b>可选</b>：签名调用方可信，防轰炸靠限流（per apiKey +
 * per target），不强制图形码（与 SSO 浏览器 {@code /api/sso/verification-code/sms} 的强制图形码
 * 不同——后者公开、需防脚本轰炸）。
 *
 * @param target 渠道（EMAIL/SMS）
 * @param value 邮箱地址（target=EMAIL）或手机号（target=SMS）
 * @param purpose 用途（REGISTER/LOGIN/RESET_PASSWORD）
 * @param captchaId 图形码 ID（可选——提供则校验，不提供则跳过）
 * @param captchaCode 图形码（可选——提供则校验，不提供则跳过）
 * @since 0.1.0
 */
public record SignedSendCodeRequest(
    @NotNull CodeTarget target,
    @NotBlank String value,
    @NotBlank String purpose,
    String captchaId,
    String captchaCode
) {}
