package com.aieducenter.aieducenteridentity.sso.domain.client;

import java.util.Objects;
import java.util.Set;

/**
 * SSO 消费方（OIDC client）——identity 消费 app-registry 的 SsoClient facet（CONTEXT「跨项目集成」）。
 *
 * <p>本服务不建 oauth_client 表；{@code clientSecretHash} 来自登记处（hash-only，永不落明文）。
 * redirect_uri 走<b>精确匹配</b>白名单（防开放重定向）；grants 决定支持的授权类型。</p>
 *
 * @param clientId         client_id
 * @param clientName       展示名（登录页显示「登录到 XXX」）
 * @param clientSecretHash client_secret 的 argon2 hash（来自 app-registry，hash-only）
 * @param redirectUris     回调白名单（精确匹配集合）
 * @param scopes           授权范围集合
 * @param grants           支持的 grant_type 集合
 * @param active           是否启用（任一禁用 → identity 拒办 SSO）
 *
 * @since 0.1.0
 */
public record SsoClient(
    String clientId,
    String clientName,
    String clientSecretHash,
    Set<String> redirectUris,
    Set<String> scopes,
    Set<String> grants,
    boolean active
) {

    public SsoClient {
        Objects.requireNonNull(clientId, "clientId must not be null");
        Objects.requireNonNull(redirectUris, "redirectUris must not be null");
        redirectUris = Set.copyOf(redirectUris);
        scopes = scopes == null ? Set.of() : Set.copyOf(scopes);
        grants = grants == null ? Set.of() : Set.copyOf(grants);
    }

    /**
     * redirect_uri 是否在白名单（精确匹配，非前缀/通配）。
     */
    public boolean hasRedirectUri(String uri) {
        return uri != null && redirectUris.contains(uri);
    }

    /**
     * 是否支持某 grant_type（如 authorization_code / refresh_token）。
     */
    public boolean supportsGrant(String grantType) {
        return grantType != null && grants.contains(grantType);
    }
}
