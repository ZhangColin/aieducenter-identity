package com.aieducenter.aieducenteridentity.verification.domain.service;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.aieducenter.aieducenteridentity.verification.config.VerificationCodeProperties;

class VerificationCodeGenerationServiceTest {

    @Test
    void given_generator_when_generate_code_then_return_6_digit() {
        // Given
        VerificationCodeGenerationService generator = new VerificationCodeGenerationService(
            new VerificationCodeProperties());

        // When
        String code = generator.generate();

        // Then
        assertThat(code).hasSize(6);
        assertThat(code).matches("\\d{6}");
    }

    @Test
    void given_generator_when_generate_multiple_times_then_return_different_codes() {
        // Given
        VerificationCodeGenerationService generator = new VerificationCodeGenerationService(
            new VerificationCodeProperties());

        // When
        String code1 = generator.generate();
        String code2 = generator.generate();

        // Then
        assertThat(code1).isNotEqualTo(code2);
    }

    @Test
    void given_generator_when_generate_code_then_between_100000_and_999999() {
        // Given
        VerificationCodeGenerationService generator = new VerificationCodeGenerationService(
            new VerificationCodeProperties());

        // When
        String code = generator.generate();
        int numericCode = Integer.parseInt(code);

        // Then
        assertThat(numericCode).isGreaterThanOrEqualTo(100000);
        assertThat(numericCode).isLessThanOrEqualTo(999999);
    }

    @Test
    void given_dev_code_configured_when_generate_then_always_return_fixed_code() {
        // Given — dev 固定码（issue #29：生成处固定，比对路径全真）
        VerificationCodeProperties properties = new VerificationCodeProperties();
        properties.setDevCode("246810");
        VerificationCodeGenerationService generator = new VerificationCodeGenerationService(properties);

        // When & Then — 每次生成都返回固定值
        assertThat(generator.generate()).isEqualTo("246810");
        assertThat(generator.generate()).isEqualTo("246810");
    }

    @Test
    void given_blank_dev_code_when_generate_then_fallback_to_random() {
        // Given — 空白 devCode 视为未配置，走原随机逻辑
        VerificationCodeProperties properties = new VerificationCodeProperties();
        properties.setDevCode("  ");
        VerificationCodeGenerationService generator = new VerificationCodeGenerationService(properties);

        // When
        String code = generator.generate();

        // Then
        assertThat(code).hasSize(6);
        assertThat(code).matches("\\d{6}");
    }
}
