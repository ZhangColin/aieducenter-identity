package com.aieducenter.aieducenteridentity.account.infrastructure.sso;

import org.springframework.stereotype.Component;

import com.aieducenter.aieducenteridentity.sso.application.SsoSessionAppService;
import com.cartisan.core.stereotype.Adapter;
import com.cartisan.core.stereotype.PortType;

/**
 * SSO 会话吊销适配器——适配 sso 上下文的 {@link SsoSessionAppService}（应用层门面），实现
 * {@link SsoSessionRevocationPort}（改密/封号踢人，issue #19）。
 *
 * <p>跨上下文经 sso 应用层接口调用，不穿透其领域仓储（六边形，见「限界上下文代码编写规范」§1.2）。</p>
 *
 * @since 0.1.0
 */
@Adapter(PortType.CLIENT)
@Component
public class SsoSessionRevocationAdapter implements SsoSessionRevocationPort {

    private final SsoSessionAppService sessionAppService;

    public SsoSessionRevocationAdapter(SsoSessionAppService sessionAppService) {
        this.sessionAppService = sessionAppService;
    }

    @Override
    public void revokeByUserId(Long userId) {
        sessionAppService.revokeByUserId(userId);
    }
}
