package com.aieducenter.aieducenteridentity.account.application.dto.response;

import java.util.Objects;

/**
 * 登录响应——OIDC 形态的可扩展结构（ADR-0002 / studio bug#4 结构）。
 *
 * <p>{@link #accessToken} + {@link #idToken} 都是 RS256-JWT（issue #11）；{@link #refreshToken} 仍占位 null
 * （不透明串服务端存，#② 填）。access JWT 同时作 Sa-Token 会话 token 值（见 {@code AccountTokenAppService}）。</p>
 *
 * @param accessToken  访问令牌（RS256-JWT，兼作 Sa-Token 会话 token）
 * @param refreshToken 刷新令牌（占位 null，#② 填）
 * @param idToken      身份令牌（RS256-JWT，OIDC 声明）
 * @param tokenType    令牌类型（Bearer）
 * @param expiresIn    accessToken 有效期（秒）
 */
public record LoginResponse(
    String accessToken,
    String refreshToken,
    String idToken,
    String tokenType,
    long expiresIn
) {
    public LoginResponse {
        Objects.requireNonNull(accessToken, "accessToken must not be null");
        if (tokenType == null || tokenType.isBlank()) {
            tokenType = "Bearer";
        }
    }

    /**
     * 由已签发的 access / id JWT 构造登录响应（issue #11）。
     *
     * @param accessTokenJwt access_token(JWT)
     * @param idTokenJwt     id_token(JWT)
     * @param expiresIn      accessToken 有效期（秒）
     */
    public static LoginResponse of(String accessTokenJwt, String idTokenJwt, long expiresIn) {
        Objects.requireNonNull(accessTokenJwt, "accessTokenJwt must not be null");
        Objects.requireNonNull(idTokenJwt, "idTokenJwt must not be null");
        return new LoginResponse(accessTokenJwt, null, idTokenJwt, "Bearer", expiresIn);
    }
}
