package com.aieducenter.aieducenteridentity.account.domain.token;

import java.util.Optional;

import com.cartisan.core.stereotype.Port;
import com.cartisan.core.stereotype.PortType;

/**
 * access_token 验签器端口（南向，对 JWT 验签能力的抽象）——{@code /userinfo} 用（issue #17）。
 *
 * <p>解析 + RS256 验签 + iss/exp 校验；任一不符返回 {@link Optional#empty()}（调用方映射为 401 {@code invalid_token}，
 * 不在此抛 OIDC 异常，以免 account 域反向依赖 sso 错误码）。验签通过返回 {@link VerifiedAccessToken}（sub + scope）。
 * 中性命名——当前唯一实现是 nimbus，未来密钥轮换 / 验签库换型都不影响调用方。</p>
 *
 * @since 0.1.0
 */
@Port(PortType.CLIENT)
public interface AccessTokenVerifier {

    /**
     * 验证紧凑序列化的 access_token。
     *
     * @param compactJwt RS256-JWT 串
     * @return 验签 + iss + exp 全通过返回 sub/scope；否则 empty
     */
    Optional<VerifiedAccessToken> verify(String compactJwt);
}
