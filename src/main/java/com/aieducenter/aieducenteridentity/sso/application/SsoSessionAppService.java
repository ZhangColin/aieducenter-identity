package com.aieducenter.aieducenteridentity.sso.application;

import org.springframework.stereotype.Service;

import com.aieducenter.aieducenteridentity.sso.domain.session.SsoSessionRepository;

/**
 * SSO 会话管理应用服务——会话吊销能力的 sso 上下文门面（issue #19）。
 *
 * <p>供其它上下文（account 改密/封号踢人）经防腐层调用，不直接外暴露 {@link SsoSessionRepository}
 * 领域仓储——六边形跨上下文走应用层接口（见「限界上下文代码编写规范」§1.2，同 verification 的
 * {@code VerificationCodeAppService} 模式）。</p>
 *
 * @since 0.1.0
 */
@Service
public class SsoSessionAppService {

    private final SsoSessionRepository sessionRepository;

    public SsoSessionAppService(SsoSessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    /**
     * 吊销某用户的所有 SSO 会话（改密/封号踢人）。会话失效后其 refresh 绑会话失效、access 15min 短命自然收尾。
     *
     * @param userId 用户 ID
     * @return 实际删除的会话数
     */
    public int revokeByUserId(Long userId) {
        return sessionRepository.deleteByUserId(userId);
    }
}
