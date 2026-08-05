package com.aieducenter.aieducenteridentity.sso.infrastructure.client;

import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.stereotype.Component;

import com.aieducenter.aieducenteridentity.sso.domain.client.ClientSecretVerifier;
import com.cartisan.core.stereotype.Adapter;
import com.cartisan.core.stereotype.PortType;

/**
 * client_secret 的 argon2 校验（#30 替 BCryptClientSecretVerifierAdapter）。
 *
 * <p>两端统一 {@code spring-security-crypto} 的 {@link Argon2PasswordEncoder}
 * （app-registry 的 {@code Argon2ClientSecretHasher} 同款、同 {@code defaultsForSpringSecurity_v5_8()} 参数）——
 * hash 自描述（盐/参数嵌入），identity 拿 app-registry 给的 hash 直接 {@code matches} 比对。
 * 端口 {@link ClientSecretVerifier} 不变，换算法不动调用方。</p>
 *
 * <p>运行时依赖 BouncyCastle：{@code spring-security-crypto} 将 {@code bcprov-jdk18on} 声明为可选，
 * Boot 3.4 BOM 不管理其版本，pom 显式钉 {@code 1.78.1}（与 app-registry 一致、匹配 spring-security 6.4.x）。</p>
 *
 * @since 0.1.0
 */
@Adapter(PortType.CLIENT)
@Component
public class Argon2ClientSecretVerifierAdapter implements ClientSecretVerifier {

    private final Argon2PasswordEncoder encoder = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();

    @Override
    public boolean matches(String rawSecret, String hashed) {
        if (rawSecret == null || hashed == null) {
            return false;
        }
        return encoder.matches(rawSecret, hashed);
    }
}
