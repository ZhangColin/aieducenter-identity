package com.aieducenter.aieducenteridentity.sso.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * /token 成功响应（OIDC Token Response，扁平 JSON，snake_case）。
 *
 * <p>注意：不套 {@code ApiResponse}——OIDC 协议端点直接返回协议形态。</p>
 *
 * @param accessToken  access_token（RS256-JWT）
 * @param tokenType    token_type（Bearer）
 * @param expiresIn    expires_in（秒）
 * @param refreshToken refresh_token（不透明，一次性轮换）
 * @param idToken      id_token（RS256-JWT，含 nonce）
 *
 * @since 0.1.0
 */
public record TokenResponse(
    @JsonProperty("access_token") String accessToken,
    @JsonProperty("token_type") String tokenType,
    @JsonProperty("expires_in") long expiresIn,
    @JsonProperty("refresh_token") String refreshToken,
    @JsonProperty("id_token") String idToken
) {
}
