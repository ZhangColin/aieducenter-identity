package com.aieducenter.aieducenteridentity.account.domain.token;

import java.time.Instant;
import java.util.List;

/**
 * id_token 的 JWT 声明（OIDC profile，ADR-0002 / issue #11）。
 *
 * <p>标准声明 {@code iss}/{@code sub}/{@code aud}/{@code iat}/{@code exp}/{@code jti} +
 * OIDC profile 声明 {@code email}/{@code email_verified}/{@code phone_number}/
 * {@code phone_number_verified}/{@code nickname}/{@code picture}。profile 声明可空——
 * 签名时 null 字段省略（OIDC 惯例，不发 null）。</p>
 *
 * @param iss                   签发方
 * @param sub                   主题（= userId）
 * @param aud                   受众
 * @param iat                   签发时刻
 * @param exp                   过期时刻
 * @param jti                   JWT 唯一 ID
 * @param email                 邮箱（可空）
 * @param emailVerified         邮箱是否已验证（可空）
 * @param phoneNumber           手机号（可空）
 * @param phoneNumberVerified   手机号是否已验证（可空）
 * @param nickname              昵称（可空）
 * @param picture               头像 URL（可空）
 * @param nonce                 OIDC nonce（消费方在 /authorize 生成，id_token 回带防重放；可空——非授权码流不传）
 *
 * @since 0.1.0
 */
public record IdTokenClaims(
    String iss,
    String sub,
    List<String> aud,
    Instant iat,
    Instant exp,
    String jti,
    String email,
    Boolean emailVerified,
    String phoneNumber,
    Boolean phoneNumberVerified,
    String nickname,
    String picture,
    String nonce
) {
}
