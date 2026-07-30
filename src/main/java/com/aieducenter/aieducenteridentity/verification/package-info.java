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
 * @since 0.1.0
 */
@BoundedContext(name = "Verification", subDomain = SubDomain.SUPPORTING)
package com.aieducenter.aieducenteridentity.verification;

import com.cartisan.core.stereotype.BoundedContext;
import com.cartisan.core.stereotype.SubDomain;
