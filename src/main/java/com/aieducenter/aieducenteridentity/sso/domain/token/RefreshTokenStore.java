package com.aieducenter.aieducenteridentity.sso.domain.token;

import java.time.Duration;
import java.util.Optional;

import com.cartisan.core.stereotype.Port;
import com.cartisan.core.stereotype.PortType;

/**
 * refresh_token 存储端口（南向，对不透明 refresh_token 持久化能力的抽象）。
 *
 * <p>refresh_token 是不透明随机串，服务端存 {@code refresh:{token}→{userId,sessionId}}（ADR-0002：refresh 总是不透明串、
 * 服务端存）。绑 SSO sessionId 实现「准 SLO」（issue #19 / CONTEXT）：登出/踢人清会话后，refresh grant 校验
 * sessionId 仍存活，否则失效。{@link #consume} 原子取删——单次使用、轮换防重放（issue #13）。</p>
 *
 * @since 0.1.0
 */
@Port(PortType.CLIENT)
public interface RefreshTokenStore {

    /**
     * 保存 refresh_token → {userId, sessionId} 映射，TTL = refresh 寿命。
     *
     * @param refreshToken 不透明 refresh_token
     * @param userId       用户 ID
     * @param sessionId    绑定的 SSO sessionId（准 SLO 会话校验；可空——遗留链路）
     * @param ttl          refresh 寿命
     */
    void save(String refreshToken, Long userId, String sessionId, Duration ttl);

    /**
     * 原子取删（GETDEL 语义）：返回并立即删除 refresh_token 对应的载荷。
     *
     * <p>单次使用——同一 refresh_token 再 consume 返回空（已轮换/已用）。用于 refresh grant 轮换。</p>
     *
     * @return 命中返回载荷（userId + sessionId）；不存在/已用/过期返回 empty
     */
    Optional<RefreshTokenPayload> consume(String refreshToken);
}
