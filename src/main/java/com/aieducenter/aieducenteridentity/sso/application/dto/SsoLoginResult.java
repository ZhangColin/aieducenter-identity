package com.aieducenter.aieducenteridentity.sso.application.dto;

/**
 * 登录成功结果——种 SSO cookie 用的 sessionId + 回调地址。
 *
 * @param sessionId   新建 SSO 会话 ID（装入 cookie）
 * @param redirectUrl {@code redirect_uri?code=&state=} 回调地址
 *
 * @since 0.1.0
 */
public record SsoLoginResult(
    String sessionId,
    String redirectUrl
) {
}
