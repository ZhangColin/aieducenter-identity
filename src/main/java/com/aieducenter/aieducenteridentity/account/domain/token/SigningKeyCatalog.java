package com.aieducenter.aieducenteridentity.account.domain.token;

import com.cartisan.core.stereotype.Port;
import com.cartisan.core.stereotype.PortType;

/**
 * 签名身份目录端口（南向）——发布本 IdP 的签名身份（issuer + 验签公钥集），供 {@code /discovery} 与 {@code /jwks}
 * 自发现（issue #17）。
 *
 * <p>issuer 与公钥同属「签名身份」：token 的 {@code iss} claim 取自 issuer，{@code /jwks} 发布公钥——
 * 对接方凭两者本地验签。归一个端口避免 sso 应用层反向依赖 account infra 配置（{@code JwtTokenProperties}）。
 * 中性命名——当前唯一实现是 nimbus，未来密钥轮换 / 多 kid 都不影响调用方。</p>
 *
 * @since 0.1.0
 */
@Port(PortType.CLIENT)
public interface SigningKeyCatalog {

    /**
     * 本 IdP 签发方（= token 的 {@code iss} claim = discovery 的 {@code issuer} 字段）。
     *
     * @return issuer URL
     */
    String issuer();

    /**
     * 当前有效的签名公钥集（公钥-only，不含私钥）。
     *
     * @return JWK Set 视图
     */
    JwkSetView publicJwkSet();
}
