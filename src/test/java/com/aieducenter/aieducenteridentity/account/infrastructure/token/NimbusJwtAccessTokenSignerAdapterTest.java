package com.aieducenter.aieducenteridentity.account.infrastructure.token;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Base64;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.aieducenter.aieducenteridentity.account.domain.token.AccessTokenClaims;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

/**
 * {@link NimbusJwtAccessTokenSignerAdapter} 单元测试——验证 RS256 签名 / 公钥验签 / 篡改失败（issue #11 验收②）。
 */
class NimbusJwtAccessTokenSignerAdapterTest {

    private static final String KID = "test-access-kid";

    private RSAKey rsaKey;
    private NimbusJwtAccessTokenSignerAdapter adapter;

    @BeforeEach
    void setUp() throws Exception {
        rsaKey = new RSAKeyGenerator(2048).keyID(KID).generate();
        adapter = new NimbusJwtAccessTokenSignerAdapter(new NimbusJwtSupport(rsaKey));
    }

    @Test
    void given_claims_when_sign_then_rs256_jwt_verifiable_with_public_key() throws Exception {
        Instant iat = Instant.now();
        Instant exp = iat.plusSeconds(900);
        AccessTokenClaims claims = new AccessTokenClaims(
            "https://identity.test", "user-123", List.of("aieducenter-identity"), iat, exp, "jti-abc");

        String jwt = adapter.sign(claims);

        // 紧凑 JWT 三段
        assertThat(jwt.split("\\.")).hasSize(3);

        SignedJWT parsed = SignedJWT.parse(jwt);
        // 头：RS256 + kid
        assertThat(parsed.getHeader().getAlgorithm()).isEqualTo(JWSAlgorithm.RS256);
        assertThat(parsed.getHeader().getKeyID()).isEqualTo(KID);
        // 签名公钥本地验通过（验收②）
        assertThat(parsed.verify(new RSASSAVerifier(rsaKey.toRSAPublicKey()))).isTrue();

        JWTClaimsSet parsedClaims = parsed.getJWTClaimsSet();
        assertThat(parsedClaims.getIssuer()).isEqualTo("https://identity.test");
        assertThat(parsedClaims.getSubject()).isEqualTo("user-123");
        assertThat(parsedClaims.getAudience()).containsExactly("aieducenter-identity");
        assertThat(parsedClaims.getJWTID()).isEqualTo("jti-abc");
        // iat/exp 用 epoch 秒写入；nimbus 识别为注册时间声明，回读为 Date（NumericDate）
        assertThat(parsedClaims.getIssueTime().toInstant().getEpochSecond()).isEqualTo(iat.getEpochSecond());
        assertThat(parsedClaims.getExpirationTime().toInstant().getEpochSecond()).isEqualTo(exp.getEpochSecond());
    }

    @Test
    void given_tampered_payload_when_verify_then_signature_invalid() throws Exception {
        String jwt = adapter.sign(new AccessTokenClaims(
            "https://identity.test", "user-123", List.of("aud"), Instant.now(),
            Instant.now().plusSeconds(60), "jti"));

        // 翻转 payload（中段）一字节 → 签名必然不匹配
        String[] parts = jwt.split("\\.", 3);
        byte[] payload = Base64.getUrlDecoder().decode(parts[1]);
        payload[0] ^= 0x01;
        String tampered = parts[0] + "."
            + Base64.getUrlEncoder().withoutPadding().encodeToString(payload) + "."
            + parts[2];

        boolean verified = verifyQuietly(tampered);
        assertThat(verified).as("篡改 payload 后验签必须失败").isFalse();
    }

    @Test
    void given_jwt_signed_by_other_key_when_verify_with_this_key_then_invalid() throws Exception {
        // 另一对密钥签的 token，用本 key 公钥验 → 失败
        RSAKey otherKey = new RSAKeyGenerator(2048).keyID("other").generate();
        NimbusJwtAccessTokenSignerAdapter otherAdapter =
            new NimbusJwtAccessTokenSignerAdapter(new NimbusJwtSupport(otherKey));
        String jwt = otherAdapter.sign(new AccessTokenClaims(
            "https://identity.test", "user-123", List.of("aud"), Instant.now(),
            Instant.now().plusSeconds(60), "jti"));

        boolean verified = SignedJWT.parse(jwt).verify(new RSASSAVerifier(rsaKey.toRSAPublicKey()));
        assertThat(verified).isFalse();
    }

    /** 篡改后可能解析失败或验签失败——两者都算「验签失败」。 */
    private boolean verifyQuietly(String compact) {
        try {
            return SignedJWT.parse(compact).verify(new RSASSAVerifier(rsaKey.toRSAPublicKey()));
        } catch (JOSEException | java.text.ParseException ex) {
            return false;
        }
    }
}
