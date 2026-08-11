package com.aieducenter.aieducenteridentity.sso.application.dto;

/**
 * {@code /api/sso/*} JSON 变体认证成功响应——200 体携带回调地址（issue #26）。
 *
 * <p>identity-web 登录/注册页是前后端分离 SPA：页面 fetch POST JSON（同源），成功后读
 * {@code redirectUrl} 由前端 {@code window.location.href} 顶层导航回业务应用——跨站最后一跳
 * 仍是浏览器导航，「零跨域 fetch」不变式成立。不能回 302：fetch 会自动跟随、跨域跟随被 CORS
 * 拦死，前端拿不到 Location。字段名 camelCase——/api/sso/* 命名空间契约。</p>
 *
 * @param redirectUrl {@code redirect_uri?code=&state=} 回调地址（与 form 变体 302 Location 同值）
 *
 * @since 0.1.0
 */
public record SsoRedirectResponse(
    String redirectUrl
) {
}
