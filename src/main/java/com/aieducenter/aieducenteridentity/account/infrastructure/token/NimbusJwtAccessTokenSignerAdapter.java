package com.aieducenter.aieducenteridentity.account.infrastructure.token;

import org.springframework.stereotype.Component;

import com.aieducenter.aieducenteridentity.account.domain.token.AccessTokenClaims;
import com.aieducenter.aieducenteridentity.account.domain.token.AccessTokenSigner;
import com.cartisan.core.stereotype.Adapter;
import com.cartisan.core.stereotype.PortType;
import com.nimbusds.jwt.JWTClaimsSet;

/**
 * access_token 签名器适配器——nimbus RS256 实现 {@link AccessTokenSigner}（ADR-0002 / issue #11）。
 *
 * <p>iat/exp 用 epoch 秒（NumericDate，JWT 规范）写入，避免触碰 {@code java.util.Date}
 * （架构守护禁止依赖 Date）。</p>
 *
 * @since 0.1.0
 */
@Adapter(PortType.CLIENT)
@Component
public class NimbusJwtAccessTokenSignerAdapter implements AccessTokenSigner {

    private final NimbusJwtSupport support;

    public NimbusJwtAccessTokenSignerAdapter(NimbusJwtSupport support) {
        this.support = support;
    }

    @Override
    public String sign(AccessTokenClaims claims) {
        JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder()
            .issuer(claims.iss())
            .subject(claims.sub())
            .audience(claims.aud())
            .claim("iat", claims.iat().getEpochSecond())
            .claim("exp", claims.exp().getEpochSecond())
            .jwtID(claims.jti());
        if (claims.scope() != null && !claims.scope().isBlank()) {
            builder.claim("scope", claims.scope());
        }
        return support.sign(builder.build());
    }
}
