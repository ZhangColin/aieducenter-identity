package com.aieducenter.aieducenteridentity.account.application.dto.command;

import jakarta.validation.constraints.NotBlank;

/**
 * 验证码登录命令（签名服务 {@code POST /api/account/authenticate-by-code} 入参，#59）。
 *
 * <p>签名调用方凭 email/phone + 验证码登录——端点先验码（purpose=LOGIN，经 {@code VerificationCodePort}）
 * 再委托 {@code AccountAuthAppService.authenticateByIdentifier}。{@code identifier} 格式不在此校验：
 * 含 {@code @} 走邮箱验码、否则走手机验码（与 {@code authenticateByIdentifier} 的 email/phone 双查口径一致）。</p>
 *
 * @param identifier 邮箱或手机号（用户输入）
 * @param code       验证码（调用方驱动 verification 上下文发码后取得）
 * @since 0.1.0
 */
public record AuthenticateByCodeCommand(
    @NotBlank String identifier,
    @NotBlank String code
) {
}
