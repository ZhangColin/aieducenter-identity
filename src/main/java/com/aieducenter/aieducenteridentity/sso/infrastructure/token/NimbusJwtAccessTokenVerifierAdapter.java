package com.aieducenter.aieducenteridentity.sso.infrastructure.token;

import java.text.ParseException;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.aieducenter.aieducenteridentity.sso.domain.token.AccessTokenVerifier;
import com.aieducenter.aieducenteridentity.sso.domain.token.VerifiedAccessToken;
import com.cartisan.core.stereotype.Adapter;
import com.cartisan.core.stereotype.PortType;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.jwt.proc.BadJWTException;
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier;

/**
 * access_token 验签器适配器——nimbus RS256 实现 {@link AccessTokenVerifier}（issue #17）。
 *
 * <p>两步校验：① RS256 公钥验签（{@link RSASSAVerifier}）；② 声明校验（{@link DefaultJWTClaimsVerifier}）——
 * iss 精确匹配本服务签发方 + exp/nbf 时间有效。任一不符或解析失败返回 empty（由 /userinfo 映射为 401
 * {@code invalid_token}，不在此抛 OIDC 异常，以免 account 域反向依赖 sso 错误码）。</p>
 *
 * <p>声明校验交给 nimbus 而非手读 {@code exp}：nimbus 解析时把注册名 {@code exp} 自动归一为 {@code java.util.Date}，
 * 手读 NumericDate 会抛 {@code ParseException}；用 {@link DefaultJWTClaimsVerifier} 由 nimbus 内部按 NumericDate 判，
 * 本类不触碰 {@code Date}（架构守护禁用 {@code java.util.Date}）。</p>
 *
 * @since 0.1.0
 */
@Adapter(PortType.CLIENT)
@Component
public class NimbusJwtAccessTokenVerifierAdapter implements AccessTokenVerifier {

    private static final Logger log = LoggerFactory.getLogger(NimbusJwtAccessTokenVerifierAdapter.class);

    private final RSAKey rsaKey;
    private final DefaultJWTClaimsVerifier<SecurityContext> claimsVerifier;

    public NimbusJwtAccessTokenVerifierAdapter(RSAKey identityRsaKey, JwtTokenProperties properties) {
        this.rsaKey = identityRsaKey;
        // exactMatch 仅设 issuer → 须精确匹配本服务签发方；acceptedAudienceValues=null → 不校验 aud。exp/nbf 总会校验。
        JWTClaimsSet exactMatch = new JWTClaimsSet.Builder().issuer(properties.getIssuer()).build();
        this.claimsVerifier = new DefaultJWTClaimsVerifier<>(exactMatch, null);
    }

    @Override
    public Optional<VerifiedAccessToken> verify(String compactJwt) {
        if (compactJwt == null || compactJwt.isBlank()) {
            return Optional.empty();
        }
        try {
            SignedJWT jwt = SignedJWT.parse(compactJwt);
            if (!jwt.verify(new RSASSAVerifier(rsaKey.toRSAPublicKey()))) {
                return Optional.empty();
            }
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            claimsVerifier.verify(claims, null);
            String sub = claims.getSubject();
            if (sub == null || sub.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(new VerifiedAccessToken(sub, claims.getStringClaim("scope")));
        } catch (ParseException | JOSEException | BadJWTException ex) {
            log.debug("access_token 验签失败", ex);
            return Optional.empty();
        }
    }
}
