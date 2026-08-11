/**
 * Account 限界上下文——平台终端用户身份基座（ADR-0001）。
 *
 * <h3>职责</h3>
 * <ul>
 *   <li>认证 / 建号 / subject 数据读（{@code AccountAuthAppService} / {@code AccountSubjectAppService}——
 *       平台用户一体化缝，sso 及未来非-SSO 应用经此消费 account，ADR-0007/0008）</li>
 *   <li>密码管理（重置密码 / 修改密码）</li>
 *   <li>个人资料（profile 查看 / 编辑）</li>
 *   <li>账号状态治理（停用 / 锁定）</li>
 * </ul>
 *
 * <p>token 三件套（OIDC 产物）随 OIDC 端点归 sso 上下文（ADR-0008）；account 回归纯身份基座。
 * 认证入口（登录 / 注册 / 登出）在 sso 上下文 {@code /api/sso/*} + OIDC 根端点；
 * 受保护接口凭 SSO cookie 经 SSO 会话过滤器认人（ADR-0004，全库只一套 SSO 会话）。</p>
 *
 * <p><b>对外端点</b>（ADR-0009 / #55）：account 的 cookie / public 自助端点（me / profile / 改密 / 重置）
 * 已迁到 sso 上下文 {@code /api/sso/*}（浏览器闭环 controller 归 sso，跨 bc 调本上下文 AppService）；
 * 本上下文下的 {@code /api/account/*} 留给签名服务（{@code @RequireSignature}，#56）。</p>
 *
 * <h3>数据模型（ADR-0001）</h3>
 * <ul>
 *   <li>{@code account} 主表：userId(=id) 锚点 + email/phone 登录定位 + password_hash(可空) + 状态</li>
 *   <li>{@code profile} 扩展表（1:1）：昵称/头像</li>
 *   <li>（{@code external_identities} 社交绑定留 Phase 3）</li>
 * </ul>
 *
 * @since 0.1.0
 */
@BoundedContext(name = "Account", subDomain = SubDomain.SUPPORTING)
package com.aieducenter.aieducenteridentity.account;

import com.cartisan.core.stereotype.BoundedContext;
import com.cartisan.core.stereotype.SubDomain;
