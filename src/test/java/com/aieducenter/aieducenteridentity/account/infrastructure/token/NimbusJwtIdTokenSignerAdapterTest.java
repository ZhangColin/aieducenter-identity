package com.aieducenter.aieducenteridentity.account.infrastructure.token;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.aieducenter.aieducenteridentity.account.domain.token.IdTokenClaims;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

/**
 * {@link NimbusJwtIdTokenSignerAdapter} 单元测试——OIDC profile 声明往返 + null 省略（issue #11）。
 */
class NimbusJwtIdTokenSignerAdapterTest {

    private RSAKey rsaKey;
    private NimbusJwtIdTokenSignerAdapter adapter;

    @BeforeEach
    void setUp() throws Exception {
        rsaKey = new RSAKeyGenerator(2048).keyID("test-id-kid").generate();
        adapter = new NimbusJwtIdTokenSignerAdapter(new NimbusJwtSupport(rsaKey));
    }

    @Test
    void given_full_profile_when_sign_then_oidc_claims_round_trip() throws Exception {
        Instant iat = Instant.now();
        Instant exp = iat.plusSeconds(900);
        IdTokenClaims claims = new IdTokenClaims(
            "https://identity.test", "user-123", List.of("aieducenter-identity"), iat, exp, "jti-id",
            "user@test.com", true,
            "+8613800138000", true,
            "阿福", "https://cdn/avatar.png");

        String jwt = adapter.sign(claims);

        assertThat(jwt.split("\\.")).hasSize(3);
        SignedJWT parsed = SignedJWT.parse(jwt);
        assertThat(parsed.getHeader().getAlgorithm()).isEqualTo(JWSAlgorithm.RS256);
        assertThat(parsed.verify(new RSASSAVerifier(rsaKey.toRSAPublicKey()))).isTrue();

        JWTClaimsSet c = parsed.getJWTClaimsSet();
        assertThat(c.getSubject()).isEqualTo("user-123");
        assertThat(c.getAudience()).containsExactly("aieducenter-identity");
        assertThat(c.getStringClaim("email")).isEqualTo("user@test.com");
        assertThat(c.getBooleanClaim("email_verified")).isTrue();
        assertThat(c.getStringClaim("phone_number")).isEqualTo("+8613800138000");
        assertThat(c.getBooleanClaim("phone_number_verified")).isTrue();
        assertThat(c.getStringClaim("nickname")).isEqualTo("阿福");
        assertThat(c.getStringClaim("picture")).isEqualTo("https://cdn/avatar.png");
    }

    @Test
    void given_null_profile_fields_when_sign_then_those_claims_omitted() throws Exception {
        IdTokenClaims claims = new IdTokenClaims(
            "https://identity.test", "user-123", List.of("aud"), Instant.now(),
            Instant.now().plusSeconds(60), "jti",
            null, null, null, null, null, null);

        JWTClaimsSet c = SignedJWT.parse(adapter.sign(claims)).getJWTClaimsSet();

        assertThat(c.getClaim("email")).isNull();
        assertThat(c.getClaim("phone_number")).isNull();
        assertThat(c.getClaim("nickname")).isNull();
        assertThat(c.getClaim("picture")).isNull();
        // 标准声明仍在
        assertThat(c.getSubject()).isEqualTo("user-123");
    }
}
