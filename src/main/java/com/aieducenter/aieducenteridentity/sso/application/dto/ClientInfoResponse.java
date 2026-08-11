package com.aieducenter.aieducenteridentity.sso.application.dto;

/**
 * {@code GET /api/sso/client-info} 响应——登录页「登录到 XXX 应用」展示（issue #24）。
 *
 * <p><b>最小字段</b>：只暴露 clientId + clientName；client_secret/redirectUris/scopes 等登记信息
 * 永不流出本端点（公开访问，未登录可查）。字段名 camelCase——/api/sso/* 命名空间契约（区别于
 * /authorize URL 参数的 snake_case）。</p>
 *
 * @since 0.1.0
 */
public record ClientInfoResponse(
    String clientId,
    String clientName
) {
}
