/**
 * Account 限界上下文——平台终端用户身份基座（ADR-0001）。
 *
 * <h3>职责</h3>
 * <ul>
 *   <li>密码管理（重置密码 / 修改密码）</li>
 *   <li>个人资料（profile 查看 / 编辑）</li>
 *   <li>token 三件套签发（{@code TokenIssuerAppService}，供 sso 上下文 OIDC {@code /token} 复用）</li>
 *   <li>账号状态治理（停用 / 锁定）</li>
 * </ul>
 *
 * <p>认证入口（登录 / 注册 / 登出）在 sso 上下文 {@code /api/auth/*} + OIDC 根端点；
 * 受保护接口凭 SSO cookie 经 SSO 会话过滤器认人（ADR-0004，全库只一套 SSO 会话）。</p>
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
