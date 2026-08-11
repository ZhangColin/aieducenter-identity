package com.aieducenter.aieducenteridentity.sso.application.dto.response;

import java.util.Objects;

/**
 * 已签发的 token 三件套——OIDC {@code /token} 端点的内部签发结果（ADR-0002 / ADR-0008）。
 *
 * <p>{@link #accessToken} + {@link #idToken} 都是 RS256-JWT（issue #11）；{@link #refreshToken} 是不透明串、
 * 服务端 Redis 存、一次性轮换（issue #13）。由 sso 上下文的 {@code TokenIssuerAppService} 签发，
 * 仅供 OIDC {@code /token} 端点组装 {@code TokenResponse} 返回给消费方 BFF（ADR-0004：token 归 /token；
 * ADR-0008：token 三件套整体归属 sso）。本类型不出 HTTP——HTTP 出口是 {@code TokenResponse}。</p>
 *
 * @param accessToken  访问令牌（RS256-JWT）
 * @param refreshToken 刷新令牌（不透明串，服务端存，绑 SSO 会话）
 * @param idToken      身份令牌（RS256-JWT，OIDC 声明）
 * @param tokenType    令牌类型（Bearer）
 * @param expiresIn    accessToken 有效期（秒）
 * @since 0.1.0
 */
public record IssuedTokens(
    String accessToken,
    String refreshToken,
    String idToken,
    String tokenType,
    long expiresIn
) {
    public IssuedTokens {
        Objects.requireNonNull(accessToken, "accessToken must not be null");
        if (tokenType == null || tokenType.isBlank()) {
            tokenType = "Bearer";
        }
    }

    /**
     * 由已签发的 access / refresh / id token 构造签发结果（issue #11 + #13）。
     *
     * @param accessTokenJwt access_token(JWT)
     * @param refreshToken   refresh_token（不透明串）
     * @param idTokenJwt     id_token(JWT)
     * @param expiresIn      accessToken 有效期（秒）
     */
    public static IssuedTokens of(String accessTokenJwt, String refreshToken, String idTokenJwt, long expiresIn) {
        Objects.requireNonNull(accessTokenJwt, "accessTokenJwt must not be null");
        Objects.requireNonNull(idTokenJwt, "idTokenJwt must not be null");
        return new IssuedTokens(accessTokenJwt, refreshToken, idTokenJwt, "Bearer", expiresIn);
    }
}
