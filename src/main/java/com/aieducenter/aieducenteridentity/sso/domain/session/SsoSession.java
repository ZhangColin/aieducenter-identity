package com.aieducenter.aieducenteridentity.sso.domain.session;

import java.time.Instant;
import java.util.Objects;

/**
 * IdP SSO 会话——identity 唯一的用户会话（ADR-0004 / CONTEXT）。
 *
 * <p>浏览器侧 SSO cookie 持不透明 {@code sessionId}；Redis 存 {@code sessionId → 本记录}。
 * <b>双超时</b>：闲置超时由 Redis key TTL（滑动）强制——key 在闲置窗口内无访问即过期；
 * 绝对超时由 {@link #absoluteExpiryAt} 强制——{@link #isExpired} 判此。findActive 命中且未绝对过期时滑动续闲置。</p>
 *
 * <p>区别于 access/id/refresh token（OIDC 产物，给消费方 BFF，浏览器不接触）。</p>
 *
 * @param sessionId       不透明会话 ID（浏览器 cookie 值）
 * @param userId          用户 ID（= Account.id，SSO sub）
 * @param displayName     显示名（写入 RequestContext.userName）
 * @param createdAt       创建时刻（判定绝对窗口起点）
 * @param absoluteExpiryAt 绝对过期时刻（createdAt + 绝对超时）
 *
 * @since 0.1.0
 */
public record SsoSession(
    String sessionId,
    Long userId,
    String displayName,
    Instant createdAt,
    Instant absoluteExpiryAt
) {

    public SsoSession {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(absoluteExpiryAt, "absoluteExpiryAt must not be null");
    }

    /**
     * 是否绝对过期。
     *
     * @param now 当前时刻
     * @return 到达/超过绝对过期时刻即 true
     */
    public boolean isExpired(Instant now) {
        return !now.isBefore(absoluteExpiryAt);
    }
}
