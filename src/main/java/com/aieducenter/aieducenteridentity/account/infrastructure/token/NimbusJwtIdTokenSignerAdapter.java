package com.aieducenter.aieducenteridentity.account.infrastructure.token;

import org.springframework.stereotype.Component;

import com.aieducenter.aieducenteridentity.account.domain.token.IdTokenClaims;
import com.aieducenter.aieducenteridentity.account.domain.token.IdTokenSigner;
import com.cartisan.core.stereotype.Adapter;
import com.cartisan.core.stereotype.PortType;
import com.nimbusds.jwt.JWTClaimsSet;

/**
 * id_token 签名器适配器——nimbus RS256 实现 {@link IdTokenSigner}（OIDC profile，ADR-0002 / issue #11）。
 *
 * <p>profile 声明（email/phone_number/nickname/picture）为 null 时省略（OIDC 惯例，不发 null）。
 * iat/exp 用 epoch 秒（NumericDate）写入，避免触碰 {@code java.util.Date}（架构守护禁止依赖 Date）。</p>
 *
 * @since 0.1.0
 */
@Adapter(PortType.CLIENT)
@Component
public class NimbusJwtIdTokenSignerAdapter implements IdTokenSigner {

    private final NimbusJwtSupport support;

    public NimbusJwtIdTokenSignerAdapter(NimbusJwtSupport support) {
        this.support = support;
    }

    @Override
    public String sign(IdTokenClaims claims) {
        JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder()
            .issuer(claims.iss())
            .subject(claims.sub())
            .audience(claims.aud())
            .claim("iat", claims.iat().getEpochSecond())
            .claim("exp", claims.exp().getEpochSecond())
            .jwtID(claims.jti());
        if (claims.email() != null) {
            builder.claim("email", claims.email());
            if (claims.emailVerified() != null) {
                builder.claim("email_verified", claims.emailVerified());
            }
        }
        if (claims.phoneNumber() != null) {
            builder.claim("phone_number", claims.phoneNumber());
            if (claims.phoneNumberVerified() != null) {
                builder.claim("phone_number_verified", claims.phoneNumberVerified());
            }
        }
        if (claims.nickname() != null) {
            builder.claim("nickname", claims.nickname());
        }
        if (claims.picture() != null) {
            builder.claim("picture", claims.picture());
        }
        if (claims.nonce() != null) {
            builder.claim("nonce", claims.nonce());
        }
        return support.sign(builder.build());
    }
}
