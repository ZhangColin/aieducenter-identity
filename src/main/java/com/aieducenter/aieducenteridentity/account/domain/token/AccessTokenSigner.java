package com.aieducenter.aieducenteridentity.account.domain.token;

import com.cartisan.core.stereotype.Port;
import com.cartisan.core.stereotype.PortType;

/**
 * access_token 签名器端口（南向，对 JWT 签名能力的抽象）。
 *
 * <p>把 {@link AccessTokenClaims} 签成紧凑 RS256-JWT。中性命名——当前唯一实现是 nimbus，
 * 未来 {@code /jwks} 轮换、密钥库换型都不影响调用方（ADR-0002 / issue #11）。</p>
 *
 * @since 0.1.0
 */
@Port(PortType.CLIENT)
public interface AccessTokenSigner {

    /**
     * 签发 access_token。
     *
     * @param claims access_token 声明
     * @return 紧凑序列化的 RS256-JWT
     */
    String sign(AccessTokenClaims claims);
}
