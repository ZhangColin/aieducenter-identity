package com.aieducenter.aieducenteridentity.sso.domain.token;

import java.time.Instant;
import java.util.List;

/**
 * access_token 的 JWT 声明（ADR-0002 / issue #11）。
 *
 * <p>基础声明 {@code iss}/{@code sub}/{@code aud}/{@code iat}/{@code exp}/{@code jti} +
 * 授权范围 {@code scope}（OIDC 惯例：空格分隔串，如 {@code "openid profile email"}；可空——非授权码流不传）。
 * {@code /userinfo} 据此过滤返回的 profile/email/phone 资料（issue #17）。角色等留到后续授权迭代。</p>
 *
 * @param iss   签发方
 * @param sub   主题（= userId，稳定身份锚点）
 * @param aud   受众
 * @param iat   签发时刻
 * @param exp   过期时刻
 * @param jti   JWT 唯一 ID
 * @param scope 授权范围（空格分隔串，可空）
 *
 * @since 0.1.0
 */
public record AccessTokenClaims(
    String iss,
    String sub,
    List<String> aud,
    Instant iat,
    Instant exp,
    String jti,
    String scope
) {
}
