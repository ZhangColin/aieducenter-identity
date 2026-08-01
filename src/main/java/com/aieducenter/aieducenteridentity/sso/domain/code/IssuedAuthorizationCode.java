package com.aieducenter.aieducenteridentity.sso.domain.code;

import java.util.Objects;

/**
 * 已签发的授权码绑定载荷——{@code /authorize} 与 {@code /api/auth/login} 发 code 时存、{@code /token} 换 code 时取。
 *
 * <p>code 本身是不透明随机串（store 生成、作 Redis key）；本记录是绑在 code 上的上下文：
 * 消费方（clientId）+ 回调（redirectUri）+ 用户（userId）+ 防重放（nonce）+ 授权范围（scope）+
 * SSO 会话（sessionId）。/token 消费 code 时校验 clientId/redirectUri 与请求一致（防 code 篡改/挪用，
 * CONTEXT 安全集）；sessionId 透传给签发的 refresh_token（绑会话，准 SLO，issue #19）。</p>
 *
 * @param clientId    消费方 client_id
 * @param redirectUri 回调地址（精确匹配白名单的那一个）
 * @param userId      已认证用户 ID
 * @param nonce       OIDC nonce（可空，回带进 id_token）
 * @param scope       授权范围（可空）
 * @param sessionId   发 code 时的 SSO sessionId（透传给 refresh 绑会话；可空）
 *
 * @since 0.1.0
 */
public record IssuedAuthorizationCode(
    String clientId,
    String redirectUri,
    Long userId,
    String nonce,
    String scope,
    String sessionId
) {

    public IssuedAuthorizationCode {
        Objects.requireNonNull(clientId, "clientId must not be null");
        Objects.requireNonNull(redirectUri, "redirectUri must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
    }
}
