package com.aieducenter.aieducenteridentity.sso.domain.session;

import java.util.Optional;

import com.cartisan.core.stereotype.Port;
import com.cartisan.core.stereotype.PortType;

/**
 * SSO 会话存储端口（南向，对 SSO 会话持久化能力的抽象）。
 *
 * <p>实现负责生成不透明 sessionId、双超时强制（闲置靠 key TTL 滑动、绝对靠存储值）、以及按 userId 索引以便踢人。
 * 登录/注册建会话、{@code /authorize} 判免登、登出/改密/封号踢人均走本端口。</p>
 *
 * @since 0.1.0
 */
@Port(PortType.REPOSITORY)
public interface SsoSessionRepository {

    /**
     * 创建新 SSO 会话（生成 sessionId、存 Redis、登记 userId 索引）。
     *
     * @param userId      用户 ID
     * @param displayName 显示名（可空）
     * @return 新建的 SSO 会话
     */
    SsoSession create(Long userId, String displayName);

    /**
     * 查找并激活会话——命中且未绝对过期时返回会话并滑动闲置超时；否则返回 empty。
     *
     * <p>key 不存在（闲置过期/已登出/未知）或已绝对过期（同时删除）均返回 empty。</p>
     *
     * @param sessionId 会话 ID
     * @return 有效会话；否则 empty
     */
    Optional<SsoSession> findActive(String sessionId);

    /**
     * 删除单个会话（登出）。
     *
     * @param sessionId 会话 ID
     */
    void delete(String sessionId);

    /**
     * 删除某用户的所有会话（改密/封号踢人）。
     *
     * @param userId 用户 ID
     * @return 实际删除的会话数
     */
    int deleteByUserId(Long userId);
}
