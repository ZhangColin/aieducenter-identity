package com.aieducenter.aieducenteridentity.sso.domain.token;

import com.cartisan.core.stereotype.Port;
import com.cartisan.core.stereotype.PortType;

/**
 * id_token 签名器端口（南向，对 JWT 签名能力的抽象）。
 *
 * <p>把 {@link IdTokenClaims}（OIDC 标准声明）签成紧凑 RS256-JWT。与 {@link AccessTokenSigner}
 * 分开——access 与 id 的声明集 / 受众 / 校验规则不同，独立端口更不把 JWT 细节泄漏到应用层（ADR-0002 / issue #11）。</p>
 *
 * @since 0.1.0
 */
@Port(PortType.CLIENT)
public interface IdTokenSigner {

    /**
     * 签发 id_token。
     *
     * @param claims id_token 声明（OIDC profile）
     * @return 紧凑序列化的 RS256-JWT
     */
    String sign(IdTokenClaims claims);
}
