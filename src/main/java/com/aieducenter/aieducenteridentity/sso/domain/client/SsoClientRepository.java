package com.aieducenter.aieducenteridentity.sso.domain.client;

import java.util.Optional;

import com.cartisan.core.stereotype.Port;
import com.cartisan.core.stereotype.PortType;

/**
 * SSO 消费方查询端口（南向，对 app-registry SsoClient facet 的抽象）。
 *
 * <p>当前唯一实现是 stub（#15 测试/dev）；#6 接真 app-registry（远程适配器 + Caffeine 缓存）替换。
 * {@code /authorize} 与 {@code /token} 凭 client_id 查询、校验 redirect_uri 白名单 / client_secret。</p>
 *
 * @since 0.1.0
 */
@Port(PortType.CLIENT)
public interface SsoClientRepository {

    /**
     * 按 client_id 查询消费方；不存在或未启用返回 empty。
     */
    Optional<SsoClient> findByClientId(String clientId);
}
