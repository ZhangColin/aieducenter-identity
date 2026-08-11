package com.aieducenter.aieducenteridentity.sso.infrastructure.token;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.aieducenter.aieducenteridentity.sso.domain.token.JwkSetView;
import com.aieducenter.aieducenteridentity.sso.domain.token.JwkView;
import com.aieducenter.aieducenteridentity.sso.domain.token.SigningKeyCatalog;
import com.cartisan.core.stereotype.Adapter;
import com.cartisan.core.stereotype.PortType;
import com.nimbusds.jose.jwk.RSAKey;

/**
 * 签名公钥目录适配器——nimbus 实现 {@link SigningKeyCatalog}（issue #17）。
 *
 * <p>从单一 {@link RSAKey} Bean 取公钥部分（{@code toPublicJWK}，不含私钥），按 JWK 标准字段组装视图。
 * access / id token 共用同一对密钥、同一 kid，故目录仅一把公钥。</p>
 *
 * @since 0.1.0
 */
@Adapter(PortType.CLIENT)
@Component
public class NimbusSigningKeyCatalogAdapter implements SigningKeyCatalog {

    private static final String USE_SIG = "sig";
    private static final String ALG_RS256 = "RS256";

    private final RSAKey rsaKey;
    private final String issuer;

    public NimbusSigningKeyCatalogAdapter(RSAKey identityRsaKey, JwtTokenProperties properties) {
        this.rsaKey = identityRsaKey;
        this.issuer = properties.getIssuer();
    }

    @Override
    public String issuer() {
        return issuer;
    }

    @Override
    public JwkSetView publicJwkSet() {
        RSAKey pub = rsaKey.toPublicJWK();
        Map<String, Object> jwk = pub.toJSONObject();
        JwkView view = new JwkView(
            (String) jwk.get("kty"),
            USE_SIG,
            ALG_RS256,
            pub.getKeyID(),
            (String) jwk.get("n"),
            (String) jwk.get("e"));
        return new JwkSetView(List.of(view));
    }
}
