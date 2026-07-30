package com.aieducenter.aieducenteridentity.account.infrastructure.security;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import com.aieducenter.aieducenteridentity.account.domain.service.AccountPasswordEncoder;
import com.cartisan.core.stereotype.Adapter;
import com.cartisan.core.stereotype.PortType;

/**
 * BCrypt 密码编码器适配器——用 Spring Security 的 {@link BCryptPasswordEncoder} 实现
 * {@link AccountPasswordEncoder} 端口。
 *
 * @since 0.1.0
 */
@Adapter(PortType.CLIENT)
@Component
public class BCryptAccountPasswordEncoderAdapter implements AccountPasswordEncoder {

    private static final int STRENGTH = 10;
    private final BCryptPasswordEncoder encoder;

    public BCryptAccountPasswordEncoderAdapter() {
        this.encoder = new BCryptPasswordEncoder(STRENGTH);
    }

    @Override
    public String encode(String plainPassword) {
        return encoder.encode(plainPassword);
    }

    @Override
    public boolean matches(String plainPassword, String encodedPassword) {
        if (encodedPassword == null) {
            return false;
        }
        return encoder.matches(plainPassword, encodedPassword);
    }
}
