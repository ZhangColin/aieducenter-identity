package com.aieducenter.aieducenteridentity.account.application.dto.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 重置密码命令（签名服务 {@code POST /api/account/reset-password} 入参，#59）。
 *
 * <p>签名调用方凭 identifier + 验证码重置密码——委托 {@code AccountPasswordAppService.resetPassword}
 * （内部验码 purpose=RESET_PASSWORD + 改密 + 踢会话）。字段名用签名服务统一词汇
 * {@code identifier}/{@code code}（与 {@code authenticate}/{@code authenticate-by-code} 一致），
 * controller 映射到既有 {@link ResetPasswordCommand}（{@code account}/{@code verificationCode}），
 * 领域逻辑不重写、不重测。</p>
 *
 * @param identifier  邮箱或手机号
 * @param code        验证码
 * @param newPassword 新明文密码（与 {@link ResetPasswordCommand#newPassword} 同强度约束）
 * @since 0.1.0
 */
public record ResetPasswordByCodeCommand(
    @NotBlank String identifier,
    @NotBlank String code,
    @NotBlank
    @Size(min = 8, max = 20)
    @Pattern(regexp = "^(?=.*[a-zA-Z])(?=.*\\d).+$")
    String newPassword
) {
}
