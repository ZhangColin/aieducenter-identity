package com.aieducenter.aieducenteridentity.verification.domain.service;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.aieducenter.aieducenteridentity.verification.config.CaptchaProperties;
import com.aieducenter.aieducenteridentity.verification.domain.service.CaptchaGenerationService.CaptchaResult;

class CaptchaGenerationServiceTest {

    @Test
    void given_default_when_generate_then_random_4_char_code_and_image() {
        // Given — 不配 dev 键，行为与现状一致（issue #29 AC6）
        CaptchaGenerationService generator = new CaptchaGenerationService(new CaptchaProperties());

        // When
        CaptchaResult result = generator.generate();

        // Then
        assertThat(result.code()).hasSize(4);
        assertThat(result.image()).startsWith("data:image/png;base64,");
        assertThat(result.image().length()).isGreaterThan(100);
    }

    @Test
    void given_dev_code_configured_when_generate_then_fixed_code_and_image_drawn() {
        // Given — dev 固定图形码（issue #29：图上画的就是固定串，人肉 QA 无感）
        CaptchaProperties properties = new CaptchaProperties();
        properties.setDevCode("qa58");
        CaptchaGenerationService generator = new CaptchaGenerationService(properties);

        // When
        CaptchaResult result = generator.generate();

        // Then — 返回文本 = 固定值，且图片照常生成（画的是固定串）
        assertThat(result.code()).isEqualTo("qa58");
        assertThat(result.image()).startsWith("data:image/png;base64,");
        assertThat(result.image().length()).isGreaterThan(100);
    }
}
