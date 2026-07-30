package com.aieducenter.aieducenteridentity.verification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.aieducenter.aieducenteridentity.verification.domain.error.CaptchaError;
import com.aieducenter.aieducenteridentity.verification.domain.repository.CaptchaRepository;
import com.aieducenter.aieducenteridentity.verification.domain.service.CaptchaGenerationService;
import com.aieducenter.aieducenteridentity.verification.domain.service.CaptchaGenerationService.CaptchaResult;
import com.cartisan.core.exception.DomainException;

@ExtendWith(MockitoExtension.class)
class CaptchaAppServiceTest {

    @Mock
    private CaptchaGenerationService generator;

    @Mock
    private CaptchaRepository repository;

    private CaptchaAppService appService;

    @BeforeEach
    void setUp() {
        appService = new CaptchaAppService(generator, repository);
    }

    @Test
    void given_validGenerator_when_createCaptcha_then_returnResponseWithImageAndId() {
        // Given
        String expectedImage = "data:image/png;base64,iVBORw0KGgo...";
        String expectedCode = "A1B2";
        when(generator.generate()).thenReturn(new CaptchaResult(expectedImage, expectedCode));

        // When
        var response = appService.createCaptcha();

        // Then
        assertThat(response.image()).isEqualTo(expectedImage);
        assertThat(response.captchaId()).isNotNull();
        assertThat(response.captchaId()).hasSize(36); // UUID format

        verify(repository).save(anyString(), eq(expectedCode), eq(180L));
    }

    @Test
    void given_correctCaptcha_when_verifyCaptcha_then_success() {
        // Given
        String captchaId = "test-captcha-id";
        String code = "A1B2";
        when(repository.verifyAndDelete(captchaId, code)).thenReturn(true);

        // When/Then
        appService.verifyCaptcha(captchaId, code);
    }

    @Test
    void given_incorrectCaptcha_when_verifyCaptcha_then_throwException() {
        // Given
        String captchaId = "test-captcha-id";
        String wrongCode = "WRONG";
        when(repository.verifyAndDelete(captchaId, wrongCode)).thenReturn(false);

        // When/Then
        assertThatThrownBy(() -> appService.verifyCaptcha(captchaId, wrongCode))
            .isInstanceOf(DomainException.class)
            .hasMessageContaining(CaptchaError.CAPTCHA_INVALID.message());
    }
}
