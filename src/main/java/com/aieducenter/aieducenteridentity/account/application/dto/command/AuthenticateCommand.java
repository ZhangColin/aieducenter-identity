package com.aieducenter.aieducenteridentity.account.application.dto.command;

import jakarta.validation.constraints.NotBlank;

/**
 * 密码验密命令（签名服务 {@code POST /api/account/authenticate} 入参，#58）。
 *
 * <p>签名调用方凭 email/phone + 明文密码验密——password 是正常服务参数（签名服务 scope，不受
 * 「应用不持有用户凭据」不变式约束，见 CONTEXT「签名服务 API」）。{@code identifier} 格式不在此校验：
 * {@code AccountAuthAppService.authenticate} 做 email/phone 双查，命中即验密、否则与密码错统一抛
 * {@code LOGIN_PASSWORD_INCORRECT}（防用户枚举）。</p>
 *
 * @param identifier 邮箱或手机号（用户输入）
 * @param password   明文密码
 * @since 0.1.0
 */
public record AuthenticateCommand(
    @NotBlank String identifier,
    @NotBlank String password
) {
}
