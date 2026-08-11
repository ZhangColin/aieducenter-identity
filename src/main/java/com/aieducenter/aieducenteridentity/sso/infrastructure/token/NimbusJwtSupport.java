package com.aieducenter.aieducenteridentity.sso.infrastructure.token;

import org.springframework.stereotype.Component;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

/**
 * nimbus RS256 签名共用助手——持单一 {@link RSAKey} + {@link RSASSASigner}，把 {@link JWTClaimsSet}
 * 签成紧凑 JWT（access / id token 共用）。
 *
 * <p>{@link RSASSASigner} 无状态、线程安全，单实例即可。</p>
 *
 * @since 0.1.0
 */
@Component
public class NimbusJwtSupport {

    private final RSAKey rsaKey;
    private final RSASSASigner signer;

    public NimbusJwtSupport(RSAKey identityRsaKey) {
        this.rsaKey = identityRsaKey;
        try {
            this.signer = new RSASSASigner(identityRsaKey);
        } catch (JOSEException ex) {
            throw new IllegalStateException("初始化 RSASSA 签名器失败", ex);
        }
    }

    /**
     * 用 RS256（头带 kid）签名给定声明集。
     *
     * @param claims 已构造好的 JWT 声明
     * @return 紧凑序列化的 RS256-JWT
     */
    public String sign(JWTClaimsSet claims) {
        try {
            JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(rsaKey.getKeyID()).build();
            SignedJWT jwt = new SignedJWT(header, claims);
            jwt.sign(signer);
            return jwt.serialize();
        } catch (JOSEException ex) {
            throw new IllegalStateException("JWT 签名失败", ex);
        }
    }
}
