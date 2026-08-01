package com.aieducenter.aieducenteridentity.sso.application.dto;

/**
 * RP-initiated 登出结果（issue #19）。
 *
 * @param redirectUrl 登出后跳转地址（{@code post_logout_redirect_uri[?state]}）；为 null 表示不重定向
 *                   （post_logout_redirect_uri 缺失/未登记/无 client_id），由调用方渲染默认响应
 *
 * @since 0.1.0
 */
public record LogoutResult(String redirectUrl) {
}
