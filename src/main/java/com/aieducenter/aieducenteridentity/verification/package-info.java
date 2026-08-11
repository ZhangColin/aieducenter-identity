/**
 * 验证码限界上下文。
 *
 * <h3>职责</h3>
 * <ul>
 *   <li>邮箱验证码生成与校验</li>
 *   <li>短信验证码生成与校验</li>
 *   <li>图形验证码生成与校验</li>
 *   <li>发送限流（邮箱/手机/IP）</li>
 * </ul>
 *
 * <p><b>对外端点</b>（ADR-0009 / #55）：公开的发码 / 验码 / 图形码端点已迁到 sso 上下文
 * {@code /api/sso/verification-code/*}、{@code /api/sso/captcha}（浏览器闭环 controller 归 sso，
 * 跨 bc 调本上下文 AppService）；本上下文下的 {@code /api/verification-codes}、{@code /api/captchas}
 * 留给签名服务（{@code @RequireSignature}，#56）。</p>
 *
 * @since 0.1.0
 */
@BoundedContext(name = "Verification", subDomain = SubDomain.SUPPORTING)
package com.aieducenter.aieducenteridentity.verification;

import com.cartisan.core.stereotype.BoundedContext;
import com.cartisan.core.stereotype.SubDomain;
