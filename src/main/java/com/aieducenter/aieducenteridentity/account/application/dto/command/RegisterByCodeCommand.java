package com.aieducenter.aieducenteridentity.account.application.dto.command;

import jakarta.validation.constraints.NotBlank;

/**
 * 验证码注册命令（签名服务 {@code POST /api/account/register} 入参 / {@code AccountAuthAppService.registerByCode}
 * 入参，#59）。
 *
 * <p>签名调用方凭 email 或 phone + 验过的码（+ 可选密码）注册到中央 Account。端点先验码
 * （purpose=REGISTER：提供的联络方式各验各的码）再委托 {@code AccountAuthAppService.register}——
 * <b>验不过不建号</b>（防占用他人联络方式）。</p>
 *
 * <ul>
 *   <li>{@code email} / {@code phone}：至少其一（由 {@code Account.register} 聚合不变量兜底 CONTACT_REQUIRED）；
 *       两者都给时验 email 的码（注册通常单联络方式 + 单码，对齐 CONTEXT 契约 {@code {email?, phone?, code, password?}}）。</li>
 *   <li>{@code code}：验证码（必填——空白等同错码，CODE_INVALID）。</li>
 *   <li>{@code password}：明文密码（可空——不设密码的账号后续用验证码登）。</li>
 * </ul>
 *
 * <p>本命令兼作 {@code registerByCode} 的应用服务入参（与 {@link RegisterAccountCommand} 区别：
 * 后者不含 {@code code}——是「调用方已验码」的信任入口，给 sso 等；本命令带 {@code code}，
 * 由 account 自验码，给签名服务）。{@code registerByCode} 验码后构造 {@link RegisterAccountCommand}
 * 委托既有 {@code register}，领域逻辑不重写。</p>
 *
 * @param email    注册邮箱（可空，与 phone 至少其一）
 * @param phone    注册手机号（可空，与 email 至少其一）
 * @param code     验证码
 * @param password 明文密码（可空）
 * @since 0.1.0
 */
public record RegisterByCodeCommand(
    String email,
    String phone,
    @NotBlank String code,
    String password
) {
}
