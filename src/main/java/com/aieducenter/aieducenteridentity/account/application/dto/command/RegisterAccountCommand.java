package com.aieducenter.aieducenteridentity.account.application.dto.command;

/**
 * 建号命令（{@code AccountAuthAppService.register} 入参，ADR-0008）。
 *
 * <p><b>不含验证码字段</b>——联络方式（email/phone）的「当场发码验证」由<b>调用方</b>（sso 等）经
 * verification 上下文完成、验过才调本命令；account 不做验码（与 {@code authenticateByIdentifier}
 * 一致：caller 验码、account 认人）。这与 {@code Account.register} 的契约吻合——「至少一联络方式，
 * 由调用方保证已当场发码验证、验过才建号」。未验的联络方式不传入，故不落库（防占用他人联络方式）。</p>
 *
 * <ul>
 *   <li>{@code email} / {@code phone}：至少其一（由 {@code Account.register} 聚合不变量兜底）；格式校验亦收在聚合</li>
 *   <li>{@code password}：明文密码（可空——不设密码的账号后续用验证码登；非空时由应用服务 encode 后落库）</li>
 * </ul>
 *
 * @param email    注册邮箱（可空，与 phone 至少其一）
 * @param phone    注册手机号（可空，与 email 至少其一）
 * @param password 明文密码（可空）
 * @since 0.1.0
 */
public record RegisterAccountCommand(
    String email,
    String phone,
    String password
) {
}
