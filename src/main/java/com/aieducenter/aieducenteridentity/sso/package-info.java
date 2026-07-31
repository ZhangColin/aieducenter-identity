/**
 * SSO 限界上下文——平台集中式 IdP 的 SSO 会话 + OIDC 授权码闭环（ADR-0004 / issue #15）。
 *
 * <h3>职责</h3>
 * <ul>
 *   <li>SSO 会话（cookie + Redis + 闲置/绝对双超时）——identity 唯一用户会话</li>
 *   <li>OIDC Authorization Endpoint {@code /authorize}（状态机：有会话发 code / 无会话跳登录页）</li>
 *   <li>认证入口 {@code /api/auth/*}（密码登录：建会话 + 种 cookie + 发 code）</li>
 *   <li>OIDC Token Endpoint {@code /token}（code 换 access/id/refresh；refresh 轮换）</li>
 *   <li>授权码存储（一次性 60s，绑 client/redirect_uri）</li>
 *   <li>SsoClient 查询（当前 stub，#6 接 app-registry）</li>
 *   <li>SSO 会话过滤器（受保护接口凭 SSO cookie 认人，替代 {@code @RequireAuth}）</li>
 * </ul>
 *
 * <h3>边界</h3>
 * <ul>
 *   <li>token 三件套签发复用 {@code account} 上下文的 {@code TokenIssuerAppService}（无 sa-token）</li>
 *   <li>密码认证复用 {@code account} 的 AccountRepository / AccountPasswordEncoderService</li>
 *   <li>/userinfo /jwks /discovery(#17)、注册(#18)、登出/准 SLO(#19)、微信(#20) 另行；不在本上下文</li>
 * </ul>
 *
 * @since 0.1.0
 */
@BoundedContext(name = "Sso", subDomain = SubDomain.SUPPORTING)
package com.aieducenter.aieducenteridentity.sso;

import com.cartisan.core.stereotype.BoundedContext;
import com.cartisan.core.stereotype.SubDomain;
