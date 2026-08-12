package com.aieducenter.aieducenteridentity.verification.application.dto;

/**
 * 签名服务裸验码响应——{@code {valid}}（#62）。
 *
 * <p>签名调用方可信，验码「老实回」{@code valid}：码对 {@code true}、码错 / 过期 / 已用
 * {@code false}（不搬 SSO 浏览器链路的防用户枚举——那是给不可信浏览器的）。故与 SSO
 * {@code /api/sso/verification-code/verify} 的 {@link VerifyCodeResult}（{@code {verified, message}}，
 * 码错抛标准错误）形态不同：本视图把码错降级成 {@code valid=false}，而非错误响应。
 *
 * @param valid 验码是否通过
 * @since 0.1.0
 */
public record CodeVerificationView(boolean valid) {}
