package com.aieducenter.aieducenteridentity.sso.infrastructure.client;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;

/**
 * {@link Argon2ClientSecretVerifierAdapter} 单测——client_secret 的 argon2 比对（#30 替 BCrypt）。
 *
 * <p>端口 {@code ClientSecretVerifier} 不变；hash 自描述，故无状态实例即可比对。两端统一
 * {@code spring-security-crypto} 的 {@link Argon2PasswordEncoder}（运行时依赖 BouncyCastle，pom 已钉
 * {@code bcprov-jdk18on:1.78.1}，CONTEXT「跨项目集成」）。</p>
 */
class Argon2ClientSecretVerifierAdapterTest {

    private final Argon2ClientSecretVerifierAdapter verifier = new Argon2ClientSecretVerifierAdapter();

    @Test
    void given_correct_secret_when_matches_then_true() {
        String hash = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8().encode("s3cret");
        assertThat(verifier.matches("s3cret", hash)).isTrue();
    }

    @Test
    void given_wrong_secret_when_matches_then_false() {
        String hash = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8().encode("s3cret");
        assertThat(verifier.matches("wrong", hash)).isFalse();
    }

    @Test
    void given_null_inputs_when_matches_then_false() {
        assertThat(verifier.matches(null, "hash")).isFalse();
        assertThat(verifier.matches("s3cret", null)).isFalse();
    }
}
