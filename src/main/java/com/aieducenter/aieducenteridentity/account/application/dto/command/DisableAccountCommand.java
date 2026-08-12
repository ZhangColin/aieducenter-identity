package com.aieducenter.aieducenteridentity.account.application.dto.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 封号命令（后台管理，ADR-0010 / #68）。
 *
 * <p>{@code reason} 必填——封号是高危治理动作，必须留原因（落审计 {@code account_operation_log.reason}）。
 * 缺失 / 空白 → 400，不办理。</p>
 *
 * @param reason 封号原因（必填）
 * @since 0.1.0
 */
public record DisableAccountCommand(
    @NotBlank(message = "封号原因不能为空")
    @Size(max = 500, message = "封号原因长度不能超过500")
    String reason
) {}
