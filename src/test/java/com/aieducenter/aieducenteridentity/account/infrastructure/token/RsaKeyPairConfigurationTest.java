package com.aieducenter.aieducenteridentity.account.infrastructure.token;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

/**
 * {@link RsaKeyPairConfiguration} 单元测试——PEM 持久密钥加载 + 缺省临时密钥生成（issue #11 验收④）。
 */
class RsaKeyPairConfigurationTest {

    @Test
    void given_pem_paths_when_load_then_signs_and_verifies_with_loaded_key() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        KeyPair kp = gen.generateKeyPair();
        String privPem = toPem("PRIVATE KEY", kp.getPrivate().getEncoded());
        String pubPem = toPem("PUBLIC KEY", kp.getPublic().getEncoded());

        JwtTokenProperties props = new JwtTokenProperties();
        props.setPrivateKeyPath("inmem:priv");
        props.setPublicKeyPath("inmem:pub");
        props.setKeyId("pem-kid");

        ResourceLoader loader = new ResourceLoader() {
            @Override
            public Resource getResource(String location) {
                String pem = "inmem:priv".equals(location) ? privPem : pubPem;
                return new ByteArrayResource(pem.getBytes(StandardCharsets.UTF_8));
            }

            @Override
            public ClassLoader getClassLoader() {
                return getClass().getClassLoader();
            }
        };

        RsaKeyPairConfiguration config = new RsaKeyPairConfiguration(props, loader);
        var rsaKey = config.identityRsaKey();

        assertThat(rsaKey.getKeyID()).isEqualTo("pem-kid");
        // 用加载的密钥签名 → 公钥验签通过
        NimbusJwtSupport support = new NimbusJwtSupport(rsaKey);
        String jwt = support.sign(new JWTClaimsSet.Builder().subject("u1").build());
        assertThat(SignedJWT.parse(jwt).verify(new RSASSAVerifier(rsaKey.toRSAPublicKey()))).isTrue();
    }

    @Test
    void given_no_pem_paths_when_load_then_ephemeral_key_generated() throws Exception {
        JwtTokenProperties props = new JwtTokenProperties(); // 路径均 null
        props.setKeyId("eph-kid");
        RsaKeyPairConfiguration config =
            new RsaKeyPairConfiguration(props, new DefaultResourceLoader());

        var rsaKey = config.identityRsaKey();

        assertThat(rsaKey.getKeyID()).isEqualTo("eph-kid");
        assertThat(rsaKey.toRSAPublicKey()).isNotNull();
        assertThat(rsaKey.toRSAPrivateKey()).isNotNull();
    }

    private static String toPem(String type, byte[] der) {
        String base64 = Base64.getEncoder().encodeToString(der);
        StringBuilder sb = new StringBuilder("-----BEGIN ").append(type).append("-----\n");
        for (int i = 0; i < base64.length(); i += 64) {
            sb.append(base64, i, Math.min(i + 64, base64.length())).append('\n');
        }
        sb.append("-----END ").append(type).append("-----\n");
        return sb.toString();
    }

    private static final class DefaultResourceLoader implements ResourceLoader {
        @Override
        public Resource getResource(String location) {
            return new ByteArrayResource(new byte[0]);
        }

        @Override
        public ClassLoader getClassLoader() {
            return getClass().getClassLoader();
        }
    }
}
