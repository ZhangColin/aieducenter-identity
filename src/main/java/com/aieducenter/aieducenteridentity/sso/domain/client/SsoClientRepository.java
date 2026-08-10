package com.aieducenter.aieducenteridentity.sso.domain.client;

import java.util.Optional;

import com.aieducenter.aieducenteridentity.sso.domain.error.OidcException;
import com.aieducenter.aieducenteridentity.sso.domain.error.SsoError;
import com.cartisan.core.stereotype.Port;
import com.cartisan.core.stereotype.PortType;

/**
 * SSO 消费方查询端口（南向，对 app-registry SsoClient facet 的抽象）。
 *
 * <p>实现为 {@code RemoteSsoClientRepositoryAdapter}（#30：远程调 app-registry bootstrap + Caffeine 30min 缓存）。
 * {@code /authorize} 与 {@code /token} 凭 client_id 查询、校验 redirect_uri 白名单 / client_secret。</p>
 *
 * @since 0.1.0
 */
@Port(PortType.CLIENT)
public interface SsoClientRepository {

    /**
     * 按 client_id 查询消费方；不存在或未启用返回 empty（404 / active=false 走负面缓存，稳定结论）。
     *
     * @throws OidcException {@link SsoError#TEMPORARILY_UNAVAILABLE}(503) ——app-registry 抖动（5xx/连接失败/超时）
     *     且无过期缓存可兜底时抛（infra 故障，ADR-0006；非 client 配置错）。调用方任其冒泡至
     *     {@code OidcExceptionHandler} 渲染 {@code {error:temporarily_unavailable}}。
     */
    Optional<SsoClient> findByClientId(String clientId);
}
