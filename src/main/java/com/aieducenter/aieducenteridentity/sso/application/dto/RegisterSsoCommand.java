package com.aieducenter.aieducenteridentity.sso.application.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * /api/auth/register 注册命令（CONTEXT 注册 / issue #18、#22）。
 *
 * <p>identity-web 注册页提交：authorize 上下文（client/redirect/state/nonce/scope）+ 联络方式（email/phone 至少其一）
 * + 当场验码（提供的联络方式各验各的码，purpose=REGISTER，验不过不建号）+ 可选密码
 * （设了密码登，没设走 /api/auth/login-code 验证码登）。
 * 「至少一联络方式」与格式校验由 {@code Account.register} 聚合不变量兜底。</p>
 *
 * @param clientId    消费方 client_id
 * @param redirectUri 回调地址（精确匹配白名单）
 * @param state       CSRF 串（可空，回带）
 * @param nonce       OIDC nonce（可空，写入 id_token）
 * @param scope       授权范围（可空，绑进 code——与二次免登同一发码契约，issue #25）
 * @param email       注册邮箱（可空，与 phone 至少其一；填了则 emailCode 必填）
 * @param phone       注册手机号（可空，与 email 至少其一；填了则 phoneCode 必填）
 * @param emailCode   邮箱验证码（先经 /api/account/verification-code/email 以 REGISTER 用途下发）
 * @param phoneCode   短信验证码（先经 /api/account/verification-code/sms 以 REGISTER 用途下发）
 * @param password    明文密码（可空——不设密码的账号后续用验证码登录）
 *
 * @since 0.1.0
 */
public record RegisterSsoCommand(
    @NotBlank(message = "client_id 不能为空") String clientId,
    @NotBlank(message = "redirect_uri 不能为空") String redirectUri,
    String state,
    String nonce,
    String scope,
    String email,
    String phone,
    String emailCode,
    String phoneCode,
    String password
) {
}
