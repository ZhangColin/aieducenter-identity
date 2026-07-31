package com.aieducenter.aieducenteridentity.account.infrastructure.token;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;

/**
 * RSA 密钥对配置——为 JWT 签名器提供单一 {@link RSAKey} Bean（ADR-0002 / issue #11）。
 *
 * <p>配了 PEM（私钥 PKCS#8 + 公钥 X.509）就加载持久密钥；否则内存生成 2048 位临时密钥并 WARN
 * （重启后已签发 token 不可验——测试/本地可接受，prod 必须配 PEM）。access / id 共用同一对密钥、同一 kid。</p>
 *
 * @since 0.1.0
 */
@Configuration
@EnableConfigurationProperties(JwtTokenProperties.class)
public class RsaKeyPairConfiguration {

    private static final Logger log = LoggerFactory.getLogger(RsaKeyPairConfiguration.class);
    private static final int KEY_SIZE_BITS = 2048;

    private final JwtTokenProperties properties;
    private final ResourceLoader resourceLoader;

    public RsaKeyPairConfiguration(JwtTokenProperties properties, ResourceLoader resourceLoader) {
        this.properties = properties;
        this.resourceLoader = resourceLoader;
    }

    /**
     * 身份域 RSA 密钥对（access / id token 共用）。
     */
    @Bean
    public RSAKey identityRsaKey() {
        if (isPresent(properties.getPrivateKeyPath()) && isPresent(properties.getPublicKeyPath())) {
            return loadFromPem(properties.getPrivateKeyPath(), properties.getPublicKeyPath(), properties.getKeyId());
        }
        log.warn("identity.token.jwt.private-key-path / public-key-path 未配置 → 生成临时 RSA 密钥对。"
            + "重启后已签发 token 将无法验签；生产环境必须配置持久 PEM。");
        try {
            return new RSAKeyGenerator(KEY_SIZE_BITS).keyID(properties.getKeyId()).generate();
        } catch (JOSEException ex) {
            throw new IllegalStateException("生成临时 RSA 密钥对失败", ex);
        }
    }

    private RSAKey loadFromPem(String privateKeyPath, String publicKeyPath, String keyId) {
        try {
            RSAPrivateKey privateKey = readPrivateKey(privateKeyPath);
            RSAPublicKey publicKey = readPublicKey(publicKeyPath);
            return new RSAKey.Builder(publicKey).privateKey(privateKey).keyID(keyId).build();
        } catch (GeneralSecurityException | IOException ex) {
            throw new IllegalStateException("加载 RSA PEM 密钥对失败：" + privateKeyPath + " / " + publicKeyPath, ex);
        }
    }

    private RSAPrivateKey readPrivateKey(String path) throws IOException, GeneralSecurityException {
        byte[] der = decodePem(readResource(path));
        return (RSAPrivateKey) KeyFactory.getInstance("RSA")
            .generatePrivate(new PKCS8EncodedKeySpec(der));
    }

    private RSAPublicKey readPublicKey(String path) throws IOException, GeneralSecurityException {
        byte[] der = decodePem(readResource(path));
        return (RSAPublicKey) KeyFactory.getInstance("RSA")
            .generatePublic(new X509EncodedKeySpec(der));
    }

    private static byte[] decodePem(String pem) {
        String base64 = pem
            .replaceAll("-----BEGIN [^-]+-----", "")
            .replaceAll("-----END [^-]+-----", "")
            .replaceAll("\\s", "");
        return Base64.getDecoder().decode(base64);
    }

    private String readResource(String path) throws IOException {
        Resource resource = resourceLoader.getResource(path);
        try (InputStream in = resource.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }
}
