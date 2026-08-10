package com.aieducenter.aieducenteridentity.sso.infrastructure.client;

import java.util.List;
import java.util.Set;

/**
 * app-registry SsoClient bootstrap 端点的返回契约（取 {@code ApiResponse.data}）——#30 远程解析。
 *
 * <p>镜像 {@code aieducenter-app-registry} 的 {@code SsoClientInfo}（HTTP 契约消费，故本仓本地落一份，
 * 不引 app-registry 源码依赖）。{@code active=false}（app/client 任一禁用）时 {@code clientSecretHash=null}——
 * identity 见 active=false / hash=null 即拒办 SSO（CONTEXT「跨项目集成」）。</p>
 *
 * @param clientId         OIDC client_id
 * @param appId            所属应用 id（identity 不用，仅契约透传）
 * @param clientName       client 名（登录页「登录到 XXX」）
 * @param clientSecretHash      client_secret 的 argon2 hash（仅 active 时返；否则 null）
 * @param redirectUris          登录回调地址列表（精确匹配白名单）
 * @param postLogoutRedirectUris 登出回跳地址列表（精确匹配白名单，与 redirectUris 平级，ADR-0005）；
 *                               app-registry 未下发时为 null——identity 侧兜底为空集（白名单为空 = 不跳转）
 * @param scopes                授权范围
 * @param grants                授权类型
 * @param active                组合生效 = client.active && app.active
 * @since 0.1.0
 */
public record SsoClientInfo(
    String clientId,
    Long appId,
    String clientName,
    String clientSecretHash,
    List<String> redirectUris,
    List<String> postLogoutRedirectUris,
    Set<String> scopes,
    Set<String> grants,
    boolean active) {
}
