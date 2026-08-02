package com.aieducenter.aieducenteridentity.sso.application.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * /api/auth/register 密码注册命令（CONTEXT 注册 / issue #18）。
 *
 * <p>identity-web 注册页提交：authorize 上下文（client/redirect/state/nonce）+ 联络方式（email/phone 至少其一）
 * + 密码。本期密码必填（验证码登录 #22 落地前，不设密码的账号登不进来）；
 * 「至少一联络方式」与格式校验由 {@code Account.register} 聚合不变量兜底。</p>
 *
 * @param clientId    消费方 client_id
 * @param redirectUri 回调地址（精确匹配白名单）
 * @param state       CSRF 串（可空，回带）
 * @param nonce       OIDC nonce（可空，写入 id_token）
 * @param email       注册邮箱（可空，与 phone 至少其一）
 * @param phone       注册手机号（可空，与 email 至少其一）
 * @param password    明文密码（必填）
 *
 * @since 0.1.0
 */
public record RegisterByPasswordSsoCommand(
    @NotBlank(message = "client_id 不能为空") String clientId,
    @NotBlank(message = "redirect_uri 不能为空") String redirectUri,
    String state,
    String nonce,
    String email,
    String phone,
    @NotBlank(message = "密码不能为空") String password
) {
}
