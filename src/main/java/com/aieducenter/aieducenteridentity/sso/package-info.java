/**
 * SSO 限界上下文——平台集中式 IdP 的 SSO 会话 + OIDC 授权码闭环（ADR-0004 / issue #15）。
 *
 * <h3>职责</h3>
 * <ul>
 *   <li>SSO 会话（cookie + Redis + 闲置/绝对双超时）——identity 唯一用户会话</li>
 *   <li>OIDC Authorization Endpoint {@code /authorize}（状态机：有会话发 code / 无会话跳登录页）</li>
 *   <li>认证入口 {@code /api/sso/*}（密码登录 / 验证码登录 / 注册：建会话 + 种 cookie + 发 code）</li>
 *   <li>SSO 浏览器闭环端点 {@code /api/sso/*}：client-info、captcha、verification-code、me / profile / 改密 / 重置
 *       （#55 收拢；cookie / public 鉴权，identity-web 用；跨 bc 调 account / verification AppService，ADR-0007）</li>
 *   <li>OIDC Token Endpoint {@code /token}（code 换 access/id/refresh；refresh 轮换）</li>
 *   <li>OIDC /userinfo /jwks /discovery（#17）、登出/准 SLO（#19）——OIDC 闭环全在 sso</li>
 *   <li>授权码存储（一次性 60s，绑 client/redirect_uri）</li>
 *   <li>SsoClient 查询（#30 消费 app-registry bootstrap + Caffeine 30min 缓存）</li>
 *   <li>SSO 会话过滤器（受保护接口凭 SSO cookie 认人，全库唯一认人入口）</li>
 * </ul>
 *
 * <h3>边界</h3>
 * <ul>
 *   <li>token 三件套<b>自有签发</b>（{@code TokenIssuerAppService}，ADR-0008）；subject 数据经 {@code account}
 *       的 {@code AccountSubjectAppService} 取（不注入 account 仓储 / 不拿 account 聚合，ADR-0007）</li>
 *   <li>认人 / 建号经 {@code account} 的 {@code AccountAuthAppService}（ADR-0007 跨上下文只走应用层）</li>
 * </ul>
 *
 * @since 0.1.0
 */
@BoundedContext(name = "Sso", subDomain = SubDomain.SUPPORTING)
package com.aieducenter.aieducenteridentity.sso;

import com.cartisan.core.stereotype.BoundedContext;
import com.cartisan.core.stereotype.SubDomain;
