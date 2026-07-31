package com.aieducenter.aieducenteridentity.sso.infrastructure.client;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class BCryptClientSecretVerifierAdapterTest {

    private final BCryptClientSecretVerifierAdapter verifier = new BCryptClientSecretVerifierAdapter();

    @Test
    void given_correct_secret_when_matches_then_true() {
        String hash = new BCryptPasswordEncoder().encode("s3cret");
        assertThat(verifier.matches("s3cret", hash)).isTrue();
    }

    @Test
    void given_wrong_secret_when_matches_then_false() {
        String hash = new BCryptPasswordEncoder().encode("s3cret");
        assertThat(verifier.matches("wrong", hash)).isFalse();
    }

    @Test
    void given_null_inputs_when_matches_then_false() {
        assertThat(verifier.matches(null, "hash")).isFalse();
        assertThat(verifier.matches("s3cret", null)).isFalse();
    }
}
