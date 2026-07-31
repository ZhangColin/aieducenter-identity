package com.aieducenter.aieducenteridentity.sso.domain.client;

import com.cartisan.core.stereotype.Port;
import com.cartisan.core.stereotype.PortType;

/**
 * client_secret 校验端口（南向，对「拿明文 secret 比对存储哈希」能力的抽象）。
 *
 * <p>CONTEXT 安全集：client_secret <b>hash-only 比对</b>，永不返回明文。#15 stub 用 BCrypt；
 * #6 接真 app-registry 时换 argon2（{@code Argon2PasswordEncoder}，届时引入 BouncyCastle）。
 * 中性端口使换算法不动调用方（{@code SsoTokenAppService}）。</p>
 *
 * @since 0.1.0
 */
@Port(PortType.CLIENT)
public interface ClientSecretVerifier {

    /**
     * 校验明文 client_secret 是否匹配存储哈希。
     *
     * @param rawSecret 请求里的明文 secret
     * @param hashed    存储的哈希
     * @return 匹配 true
     */
    boolean matches(String rawSecret, String hashed);
}
