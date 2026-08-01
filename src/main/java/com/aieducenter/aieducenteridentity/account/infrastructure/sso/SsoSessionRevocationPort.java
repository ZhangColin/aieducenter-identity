package com.aieducenter.aieducenteridentity.account.infrastructure.sso;

import com.cartisan.core.stereotype.Port;
import com.cartisan.core.stereotype.PortType;

/**
 * SSO 会话吊销端口——account 上下文南向调用 sso 上下文的「按 userId 清所有 SSO 会话」能力（issue #19）。
 *
 * <p>account 依赖此端口而非 sso 的会话仓储，保持上下文间解耦（六边形防腐层，同 {@code VerificationCodePort}）。
 * 改密/封号踢人走本端口：清该 userId 所有 SSO 会话 → refresh 绑会话失效 + access 短命自然收尾（准 SLO）。</p>
 *
 * @since 0.1.0
 */
@Port(PortType.CLIENT)
public interface SsoSessionRevocationPort {

    /**
     * 吊销某用户的所有 SSO 会话（登出/改密/封号踢人）。
     *
     * @param userId 用户 ID
     */
    void revokeByUserId(Long userId);
}
