package com.aieducenter.aieducenteridentity.account.domain.token;

import java.time.Instant;

import com.cartisan.core.stereotype.Port;
import com.cartisan.core.stereotype.PortType;

/**
 * IdP 会话注册器端口（南向，对「建立登录会话」能力的抽象）。
 *
 * <p>登录签发 access_token 后，用其 <b>作会话 token 的值</b>建立 IdP 会话——
 * 即 Sa-Token 的 {@code jwt→userId} 映射。Sa-Token 把 JWT 当不透明 key 存（不验签；
 * 签名给外部对接方 / 未来 {@code /jwks}），从而现有 {@code @RequireAuth} 链路全不变（ADR-0003 / issue #11）。</p>
 *
 * <p>当前唯一实现是 Sa-Token；中性命名便于 #7/#8（OIDC {@code /token}）复用同一抽象。</p>
 *
 * @since 0.1.0
 */
@Port(PortType.CLIENT)
public interface IdpSessionRegistrar {

    /**
     * 以 access JWT 为会话 token 值建立 IdP 会话。
     *
     * <p>须保留 bug#1：把 {@code displayName} 写入会话，SecurityFilter 后续请求据此填充 RequestContext。</p>
     *
     * @param userId      用户 ID（= Sa-Token loginId）
     * @param displayName 显示名（写入会话供 RequestContext 读取；可空）
     * @param accessJwt   access_token(JWT)，兼作会话 token 值
     * @param ttlSeconds  会话有效期（秒），与 access TTL 对齐
     * @return 会话绝对过期时刻（now + ttlSeconds）
     */
    Instant registerSession(Long userId, String displayName, String accessJwt, long ttlSeconds);
}
