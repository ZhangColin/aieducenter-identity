package com.aieducenter.aieducenteridentity.account.application.dto.command;

import jakarta.validation.constraints.NotBlank;

/**
 * refresh_token 刷新请求（issue #13）。
 *
 * @param refreshToken 不透明 refresh_token
 */
public record RefreshTokenCommand(
    @NotBlank(message = "refresh_token 不能为空") String refreshToken
) {
}
