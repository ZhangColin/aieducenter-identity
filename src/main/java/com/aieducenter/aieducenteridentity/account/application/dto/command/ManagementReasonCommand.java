package com.aieducenter.aieducenteridentity.account.application.dto.command;

import jakarta.validation.constraints.Size;

/**
 * 管理操作命令（后台管理，ADR-0010 / #69）——解封 / 解锁 / 独立踢人等<b>原因可选</b>的管理动作共用。
 *
 * <p>区别于 {@code DisableAccountCommand}（封号 reason 必填——高危动作必须留原因）：本命令承载的是
 * 低危 / 可逆的管理动作，{@code reason} 可空（落审计 {@code account_operation_log.reason} 为 null），
 * 调用方按需附带（如「申诉成功」「风控误判」「安全处置」）。命令本身<b>可整体缺省</b>——这些端点允许
 * 空 body（{@code @RequestBody(required = false)}），未带则 reason 为 null。</p>
 *
 * @param reason 操作原因（可空）
 * @since 0.1.0
 */
public record ManagementReasonCommand(
    @Size(max = 500, message = "操作原因长度不能超过500")
    String reason
) {

    /**
     * Null-safe 取 reason——这些端点 {@code @RequestBody(required = false)}，缺省 body 时 command 为 null。
     *
     * @param command 命令（可空）
     * @return reason（command 为空则 null）
     */
    public static String reasonOrNull(ManagementReasonCommand command) {
        return command == null ? null : command.reason();
    }
}
