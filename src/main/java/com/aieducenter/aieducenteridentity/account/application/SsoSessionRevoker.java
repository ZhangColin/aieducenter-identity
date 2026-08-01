package com.aieducenter.aieducenteridentity.account.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.aieducenter.aieducenteridentity.account.infrastructure.sso.SsoSessionRevocationPort;

/**
 * SSO 会话吊销的 account 侧执行器——包装 {@link SsoSessionRevocationPort}，吞掉异常并告警，
 * 避免踢人失败回滚账号变更事务（改密/封号）。改密与封号共用本类（issue #19）。
 *
 * @since 0.1.0
 */
@Component
public class SsoSessionRevoker {

    private static final Logger log = LoggerFactory.getLogger(SsoSessionRevoker.class);

    private final SsoSessionRevocationPort port;

    public SsoSessionRevoker(SsoSessionRevocationPort port) {
        this.port = port;
    }

    /**
     * 吊销某用户所有 SSO 会话；失败仅告警不抛（不阻断账号变更主流程）。
     *
     * @param userId 用户 ID
     */
    public void revokeQuietly(Long userId) {
        try {
            port.revokeByUserId(userId);
        } catch (Exception e) {
            log.warn("revoke SSO sessions failed userId={}", userId, e);
        }
    }
}
