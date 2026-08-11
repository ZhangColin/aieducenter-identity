package com.aieducenter.aieducenteridentity.sso.infrastructure.token;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * JWT token 配置（{@code identity.token.jwt.*}，ADR-0002 / issue #11）。
 *
 * <p>iss / aud 走可配默认值（直登场景无 OAuth client，Phase 2 的 OIDC {@code /token} 再按 client_id 覆盖 aud）。
 * 密钥：{@code privateKeyPath}/{@code publicKeyPath} 都配 PEM 才持久化；否则启动生成临时密钥（重启失效）。</p>
 *
 * @since 0.1.0
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "identity.token.jwt")
public class JwtTokenProperties {

    /** 签发方（iss）。 */
    private String issuer = "https://identity.aieducenter.com";

    /** access_token / 会话有效期（秒），默认 15 分钟。 */
    private long accessTtlSeconds = 900;

    /** refresh_token 有效期（秒），默认 7 天（issue #13）。 */
    private long refreshTtlSeconds = 604800;

    /** 密钥 ID（kid，写入 JWT 头）。 */
    private String keyId = "identity-rs256-v1";

    /** 默认受众（aud）。 */
    private List<String> audiences = new ArrayList<>(List.of("aieducenter-identity"));

    /** RSA 私钥 PEM（PKCS#8）路径；不配则内存生成临时密钥。支持 classpath:/file: 前缀。 */
    private String privateKeyPath;

    /** RSA 公钥 PEM（X.509 SubjectPublicKeyInfo）路径；不配则内存生成临时密钥。 */
    private String publicKeyPath;
}
