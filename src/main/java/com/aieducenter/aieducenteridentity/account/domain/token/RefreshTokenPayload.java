package com.aieducenter.aieducenteridentity.account.domain.token;

import java.util.Objects;

/**
 * refresh_token 服务端存储载荷——绑 userId + SSO sessionId（准 SLO，issue #19）。
 *
 * <p>CONTEXT「refresh 绑 SSO 会话（过期即失效）」：登出/改密/封号清了 SSO 会话后，绑该会话的 refresh
 * 在 refresh grant 时校验 {@code sessionId} 仍存活，否则拒发新 token（access 15min 短命自然收尾，
 * 实现「登出后 ≤15min 全失效」的准 SLO）。{@code sessionId} 可空——仅遗留 sa-token 链路（{@code /api/account/login}
 * 等，#21 删）签发的 refresh 不绑会话。</p>
 *
 * @param userId    用户 ID
 * @param sessionId 签发 refresh 时的 SSO sessionId（绑会话校验；可空——遗留链路）
 *
 * @since 0.1.0
 */
public record RefreshTokenPayload(Long userId, String sessionId) {

    public RefreshTokenPayload {
        Objects.requireNonNull(userId, "userId must not be null");
    }
}
