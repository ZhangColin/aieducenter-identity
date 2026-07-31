package com.aieducenter.aieducenteridentity.sso.application.dto;

/**
 * /token 请求（OIDC Token Request，表单参数）。
 *
 * @param grantType    grant_type（authorization_code / refresh_token）
 * @param code         授权码（code grant）
 * @param redirectUri  回调地址（code grant，须与 /authorize 一致）
 * @param clientId     client_id
 * @param clientSecret client_secret（机密客户端，client_secret_post）
 * @param refreshToken refresh_token（refresh grant）
 *
 * @since 0.1.0
 */
public record TokenRequest(
    String grantType,
    String code,
    String redirectUri,
    String clientId,
    String clientSecret,
    String refreshToken
) {
}
