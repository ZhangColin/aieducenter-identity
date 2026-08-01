package com.aieducenter.aieducenteridentity.account.domain.token;

/**
 * 已验签的 access_token 摘要——{@code /userinfo} 据此定位用户 + 按授权范围过滤资料（issue #17）。
 *
 * @param sub   主题（= userId 字符串）
 * @param scope 授权范围（空格分隔串，可空——无 scope 时 /userinfo 仅返回 sub）
 *
 * @since 0.1.0
 */
public record VerifiedAccessToken(
    String sub,
    String scope
) {
}
