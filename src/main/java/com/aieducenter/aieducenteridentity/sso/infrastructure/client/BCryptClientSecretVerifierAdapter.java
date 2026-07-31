package com.aieducenter.aieducenteridentity.sso.infrastructure.client;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import com.aieducenter.aieducenteridentity.sso.domain.client.ClientSecretVerifier;
import com.cartisan.core.stereotype.Adapter;
import com.cartisan.core.stereotype.PortType;

/**
 * client_secret 的 BCrypt 校验（#15 stub 期）。
 *
 * <p>BCrypt 哈希自描述（盐+强度嵌入），故无状态实例即可比对。#6 接真 app-registry 时换 argon2 实现
 * （引入 BouncyCastle + {@code Argon2PasswordEncoder}），端口不变。</p>
 *
 * @since 0.1.0
 */
@Adapter(PortType.CLIENT)
@Component
public class BCryptClientSecretVerifierAdapter implements ClientSecretVerifier {

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Override
    public boolean matches(String rawSecret, String hashed) {
        if (rawSecret == null || hashed == null) {
            return false;
        }
        return encoder.matches(rawSecret, hashed);
    }
}
