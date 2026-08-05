package com.aieducenter.aieducenteridentity.sso.domain.client;

import com.cartisan.core.stereotype.Port;
import com.cartisan.core.stereotype.PortType;

/**
 * client_secret 校验端口（南向，对「拿明文 secret 比对存储哈希」能力的抽象）。
 *
 * <p>CONTEXT 安全集：client_secret <b>hash-only 比对</b>，永不返回明文。实现为 {@code Argon2ClientSecretVerifierAdapter}
 * （#30：{@code Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8()}，与 app-registry 同款、BouncyCastle 运行时依赖）。
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
