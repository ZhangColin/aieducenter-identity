package com.aieducenter.aieducenteridentity.verification.config;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.StandardEnvironment;

/**
 * {@link DevCodeProdGuard} 单元测试（issue #29 AC5）。
 *
 * <p>prod profile 配任一 dev 固定码键 → 启动 fail-fast 且消息指明具体键名；
 * prod 未配 / 非 prod 配键 → 放行。</p>
 */
class DevCodeProdGuardTest {

    private static StandardEnvironment envWithProfiles(String... profiles) {
        StandardEnvironment env = new StandardEnvironment();
        env.setActiveProfiles(profiles);
        return env;
    }

    @Test
    void given_prod_profile_and_code_dev_key_when_check_then_throw_with_key_name() {
        // Given
        VerificationCodeProperties codeProperties = new VerificationCodeProperties();
        codeProperties.setDevCode("246810");
        DevCodeProdGuard guard = new DevCodeProdGuard(
            envWithProfiles("prod"), codeProperties, new CaptchaProperties());

        // When & Then
        assertThatThrownBy(guard::checkDevCodeNotInProd)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("verification.code.dev-code");
    }

    @Test
    void given_prod_profile_and_captcha_dev_key_when_check_then_throw_with_key_name() {
        // Given
        CaptchaProperties captchaProperties = new CaptchaProperties();
        captchaProperties.setDevCode("qa58");
        DevCodeProdGuard guard = new DevCodeProdGuard(
            envWithProfiles("prod"), new VerificationCodeProperties(), captchaProperties);

        // When & Then
        assertThatThrownBy(guard::checkDevCodeNotInProd)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("verification.captcha.dev-code");
    }

    @Test
    void given_prod_profile_without_dev_keys_when_check_then_pass() {
        // Given
        DevCodeProdGuard guard = new DevCodeProdGuard(
            envWithProfiles("prod"), new VerificationCodeProperties(), new CaptchaProperties());

        // When & Then — 不抛
        assertThatCode(guard::checkDevCodeNotInProd).doesNotThrowAnyException();
    }

    @Test
    void given_non_prod_profile_with_dev_keys_when_check_then_pass() {
        // Given — local 配固定码是正经用法（application-local.yml）
        VerificationCodeProperties codeProperties = new VerificationCodeProperties();
        codeProperties.setDevCode("246810");
        CaptchaProperties captchaProperties = new CaptchaProperties();
        captchaProperties.setDevCode("qa58");
        DevCodeProdGuard guard = new DevCodeProdGuard(
            envWithProfiles("local"), codeProperties, captchaProperties);

        // When & Then — 不抛
        assertThatCode(guard::checkDevCodeNotInProd).doesNotThrowAnyException();
    }
}
