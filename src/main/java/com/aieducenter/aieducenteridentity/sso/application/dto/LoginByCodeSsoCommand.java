package com.aieducenter.aieducenteridentity.sso.application.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * /api/sso/login-code 验证码登录命令（CONTEXT 登录契约 / issue #22）。
 *
 * <p>identity-web 登录页提交：authorize 上下文（client/redirect/state/nonce/scope）+ 联络方式（account：
 * 邮箱或手机号）+ 验证码（purpose=LOGIN，经 verification 上下文验）。与 /api/sso/login 同一契约，
 * 仅凭据字段不同（code 换 password）。</p>
 *
 * @param clientId    消费方 client_id
 * @param redirectUri 回调地址（精确匹配白名单）
 * @param state       CSRF 串（可空，回带）
 * @param nonce       OIDC nonce（可空，写入 id_token）
 * @param scope       授权范围（可空，绑进 code——与二次免登同一发码契约，issue #25）
 * @param account     登录账号（邮箱或手机号）
 * @param code        验证码（先经 /api/sso/verification-code/* 以 LOGIN 用途下发）
 *
 * @since 0.1.0
 */
public record LoginByCodeSsoCommand(
    @NotBlank(message = "client_id 不能为空") String clientId,
    @NotBlank(message = "redirect_uri 不能为空") String redirectUri,
    String state,
    String nonce,
    String scope,
    @NotBlank(message = "账号不能为空") String account,
    @NotBlank(message = "验证码不能为空") String code
) {
}
