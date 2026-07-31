package com.aieducenter.aieducenteridentity.account.domain.token;

import java.time.Instant;
import java.util.List;

/**
 * access_token 的 JWT 声明（ADR-0002 / issue #11）。
 *
 * <p>lean 形态：仅 {@code iss}/{@code sub}/{@code aud}/{@code iat}/{@code exp}/{@code jti}。
 * {@code scope}/角色等留到后续授权迭代。</p>
 *
 * @param iss 签发方
 * @param sub 主题（= userId，稳定身份锚点）
 * @param aud 受众
 * @param iat 签发时刻
 * @param exp 过期时刻
 * @param jti JWT 唯一 ID
 *
 * @since 0.1.0
 */
public record AccessTokenClaims(
    String iss,
    String sub,
    List<String> aud,
    Instant iat,
    Instant exp,
    String jti
) {
}
