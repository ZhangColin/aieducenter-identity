package com.aieducenter.aieducenteridentity.sso.application.dto;

/**
 * /authorize 请求参数（OIDC Authorization Request 的子集）。
 *
 * @param clientId    必填，消费方 client_id
 * @param redirectUri 必填，回调地址（须精确匹配白名单）
 * @param state       可空，消费方生成的 CSRF 串（透传回 callback）
 * @param nonce       可空，消费方生成的重放串（写入 id_token）
 * @param scope       可空，授权范围
 *
 * @since 0.1.0
 */
public record AuthorizeRequest(
    String clientId,
    String redirectUri,
    String state,
    String nonce,
    String scope
) {
}
