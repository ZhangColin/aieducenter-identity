package com.aieducenter.aieducenteridentity.account.infrastructure.token;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.aieducenter.aieducenteridentity.account.domain.token.AccessTokenClaims;
import com.aieducenter.aieducenteridentity.account.domain.token.VerifiedAccessToken;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;

/**
 * {@link NimbusJwtAccessTokenVerifierAdapter} 单元测试——验签 / iss / exp / scope 解析（issue #17）。
 */
class NimbusJwtAccessTokenVerifierAdapterTest {

    private static final String ISSUER = "https://identity.test";
    private static final String KID = "verifier-kid";

    private NimbusJwtAccessTokenSignerAdapter signer;
    private NimbusJwtAccessTokenVerifierAdapter verifier;

    @BeforeEach
    void setUp() throws Exception {
        RSAKey rsaKey = new RSAKeyGenerator(2048).keyID(KID).generate();
        NimbusJwtSupport support = new NimbusJwtSupport(rsaKey);
        signer = new NimbusJwtAccessTokenSignerAdapter(support);
        JwtTokenProperties properties = new JwtTokenProperties();
        properties.setIssuer(ISSUER);
        verifier = new NimbusJwtAccessTokenVerifierAdapter(rsaKey, properties);
    }

    private AccessTokenClaims claims(String iss, Instant exp, String scope) {
        return new AccessTokenClaims(iss, "12345", List.of("aud"), Instant.now(), exp, "jti", scope);
    }

    @Test
    void given_valid_jwt_when_verify_then_returns_sub_and_scope() {
        String jwt = signer.sign(claims(ISSUER, Instant.now().plusSeconds(60), "openid email"));

        Optional<VerifiedAccessToken> result = verifier.verify(jwt);

        assertThat(result).isPresent();
        assertThat(result.get().sub()).isEqualTo("12345");
        assertThat(result.get().scope()).isEqualTo("openid email");
    }

    @Test
    void given_expired_jwt_when_verify_then_empty() {
        String jwt = signer.sign(claims(ISSUER, Instant.now().minusSeconds(60), "openid"));

        assertThat(verifier.verify(jwt)).isEmpty();
    }

    @Test
    void given_wrong_issuer_when_verify_then_empty() {
        String jwt = signer.sign(claims("https://someone-else.test", Instant.now().plusSeconds(60), "openid"));

        assertThat(verifier.verify(jwt)).isEmpty();
    }

    @Test
    void given_garbage_when_verify_then_empty() {
        assertThat(verifier.verify("not.a.jwt")).isEmpty();
        assertThat(verifier.verify("")).isEmpty();
        assertThat(verifier.verify(null)).isEmpty();
    }
}
