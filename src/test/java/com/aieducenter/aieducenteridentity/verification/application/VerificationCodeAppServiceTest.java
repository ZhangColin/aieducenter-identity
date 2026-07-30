package com.aieducenter.aieducenteridentity.verification.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.aieducenter.aieducenteridentity.verification.application.dto.SendEmailCodeCommand;
import com.aieducenter.aieducenteridentity.verification.application.dto.SendSmsCodeCommand;
import com.aieducenter.aieducenteridentity.verification.application.dto.SendCodeResponse;
import com.aieducenter.aieducenteridentity.verification.application.dto.VerifyCodeCommand;
import com.aieducenter.aieducenteridentity.verification.application.dto.VerifyCodeResult;
import com.aieducenter.aieducenteridentity.verification.application.dto.VerifySmsCodeCommand;
import com.aieducenter.aieducenteridentity.verification.config.VerificationCodeProperties;
import com.aieducenter.aieducenteridentity.verification.domain.error.VerificationCodeError;
import com.aieducenter.aieducenteridentity.verification.domain.aggregate.VerificationCode;
import com.aieducenter.aieducenteridentity.verification.domain.enums.VerificationPurpose;
import com.aieducenter.aieducenteridentity.verification.domain.enums.VerificationType;
import com.aieducenter.aieducenteridentity.verification.domain.repository.VerificationCodeRepository;
import com.aieducenter.aieducenteridentity.verification.domain.service.VerificationCodeGenerationService;
import com.aieducenter.aieducenteridentity.verification.domain.service.MessageSender;
import com.cartisan.core.exception.DomainException;

@ExtendWith(MockitoExtension.class)
class VerificationCodeAppServiceTest {

    @Mock
    private VerificationCodeRepository repository;

    @Mock
    private VerificationCodeGenerationService generator;

    @Mock
    private MessageSender messageSender;

    @Mock
    private VerificationCodeProperties properties;

    @Mock
    private CaptchaAppService captchaAppService;

    @InjectMocks
    private VerificationCodeAppService service;

    @Test
    void given_valid_email_and_no_rate_limit_when_send_verification_code_then_success() {
        // Given
        when(properties.getExpireMinutes()).thenReturn(5);
        when(properties.getEmailCooldownSeconds()).thenReturn(60L);
        when(properties.getIpMaxPerHour()).thenReturn(10);

        SendEmailCodeCommand command = new SendEmailCodeCommand("test@example.com", "REGISTER");
        when(generator.generate()).thenReturn("123456");
        when(repository.tryAcquireEmailLock("test@example.com", "REGISTER")).thenReturn(true);
        when(repository.checkAndIncrementIp("127.0.0.1")).thenReturn(1L);

        // When
        SendCodeResponse response = service.sendEmailVerificationCode(command, "127.0.0.1");

        // Then
        assertThat(response.expireInSeconds()).isEqualTo(300);
        assertThat(response.resentAfterSeconds()).isEqualTo(60);
        verify(generator).generate();
        verify(repository).save(any(VerificationCode.class));
        verify(repository).tryAcquireEmailLock("test@example.com", "REGISTER");
        verify(repository).checkAndIncrementIp("127.0.0.1");
        verify(messageSender).send("test@example.com", "123456", VerificationPurpose.REGISTER);
    }

    @Test
    void given_invalid_email_format_when_send_verification_code_then_throw_exception() {
        // Given
        SendEmailCodeCommand command = new SendEmailCodeCommand("invalid-email", "REGISTER");

        // When & Then
        assertThatThrownBy(() -> service.sendEmailVerificationCode(command, "127.0.0.1"))
            .isInstanceOf(DomainException.class)
            .hasMessageContaining(VerificationCodeError.EMAIL_INVALID.message());
    }

    @Test
    void given_email_rate_limited_when_send_verification_code_then_throw_exception() {
        // Given
        when(repository.tryAcquireEmailLock("test@example.com", "REGISTER")).thenReturn(false);

        SendEmailCodeCommand command = new SendEmailCodeCommand("test@example.com", "REGISTER");

        // When & Then
        assertThatThrownBy(() -> service.sendEmailVerificationCode(command, "127.0.0.1"))
            .isInstanceOf(DomainException.class)
            .hasMessageContaining(VerificationCodeError.RATE_LIMIT_EMAIL.message());
    }

    @Test
    void given_ip_rate_limited_when_send_verification_code_then_throw_exception() {
        // Given
        when(properties.getIpMaxPerHour()).thenReturn(10);
        when(repository.tryAcquireEmailLock("test@example.com", "REGISTER")).thenReturn(true);
        when(repository.checkAndIncrementIp("127.0.0.1")).thenReturn(11L);

        SendEmailCodeCommand command = new SendEmailCodeCommand("test@example.com", "REGISTER");

        // When & Then
        assertThatThrownBy(() -> service.sendEmailVerificationCode(command, "127.0.0.1"))
            .isInstanceOf(DomainException.class)
            .hasMessageContaining(VerificationCodeError.RATE_LIMIT_IP.message());
    }

    @Test
    void given_valid_code_when_verify_code_then_success_and_mark_used() {
        // Given
        VerifyCodeCommand command = new VerifyCodeCommand("test@example.com", "123456", "REGISTER");
        when(repository.verifyAndMarkAsUsed("test@example.com:REGISTER", "123456")).thenReturn(true);

        // When
        VerifyCodeResult result = service.verifyCode(command);

        // Then
        assertThat(result.verified()).isTrue();
        verify(repository).verifyAndMarkAsUsed("test@example.com:REGISTER", "123456");
    }

    @Test
    void given_invalid_code_when_verify_code_then_throw_exception() {
        // Given
        VerifyCodeCommand command = new VerifyCodeCommand("test@example.com", "999999", "REGISTER");
        VerificationCode code = VerificationCode.create(
            VerificationType.EMAIL, "test@example.com", "123456", VerificationPurpose.REGISTER);
        when(repository.verifyAndMarkAsUsed("test@example.com:REGISTER", "999999")).thenReturn(false);
        when(repository.findById("test@example.com:REGISTER"))
            .thenReturn(java.util.Optional.of(code));

        // When & Then
        assertThatThrownBy(() -> service.verifyCode(command))
            .isInstanceOf(DomainException.class);
    }

    @Test
    void given_code_not_found_when_verify_code_then_throw_invalid_exception() {
        // Given
        VerifyCodeCommand command = new VerifyCodeCommand("test@example.com", "123456", "REGISTER");
        when(repository.verifyAndMarkAsUsed("test@example.com:REGISTER", "123456")).thenReturn(false);
        when(repository.findById("test@example.com:REGISTER"))
            .thenReturn(java.util.Optional.empty());

        // When & Then
        assertThatThrownBy(() -> service.verifyCode(command))
            .isInstanceOf(DomainException.class)
            .hasMessageContaining(VerificationCodeError.CODE_INVALID.message());
    }

    @Test
    void given_ip_count_at_limit_when_send_verification_code_then_success() {
        // Given — count == limit (<=, not <)
        when(properties.getExpireMinutes()).thenReturn(5);
        when(properties.getEmailCooldownSeconds()).thenReturn(60L);
        when(properties.getIpMaxPerHour()).thenReturn(10);
        when(repository.tryAcquireEmailLock("test@example.com", "REGISTER")).thenReturn(true);
        when(repository.checkAndIncrementIp("127.0.0.1")).thenReturn(10L);
        when(generator.generate()).thenReturn("123456");

        SendEmailCodeCommand command = new SendEmailCodeCommand("test@example.com", "REGISTER");

        // When & Then — exactly at limit should succeed
        assertThatCode(() -> service.sendEmailVerificationCode(command, "127.0.0.1"))
            .doesNotThrowAnyException();
    }

    @Test
    void given_invalid_email_when_verify_code_then_throw_exception() {
        // Given
        VerifyCodeCommand command = new VerifyCodeCommand("invalid-email", "123456", "REGISTER");

        // When & Then
        assertThatThrownBy(() -> service.verifyCode(command))
            .isInstanceOf(DomainException.class)
            .hasMessageContaining(VerificationCodeError.EMAIL_INVALID.message());
    }

    @Test
    void given_code_already_used_when_verify_code_then_throw_code_already_used_exception() {
        // Given
        VerifyCodeCommand command = new VerifyCodeCommand("test@example.com", "123456", "REGISTER");
        when(repository.verifyAndMarkAsUsed("test@example.com:REGISTER", "123456")).thenReturn(false);
        VerificationCode usedCode = VerificationCode.restore(
            "test@example.com:REGISTER", VerificationType.EMAIL, "test@example.com",
            "123456", java.time.Instant.now().plusSeconds(300), true, VerificationPurpose.REGISTER
        );
        when(repository.findById("test@example.com:REGISTER")).thenReturn(java.util.Optional.of(usedCode));

        // When & Then
        assertThatThrownBy(() -> service.verifyCode(command))
            .isInstanceOf(DomainException.class)
            .hasMessageContaining(VerificationCodeError.CODE_ALREADY_USED.message());
    }

    @Test
    void given_code_expired_when_verify_code_then_throw_code_expired_exception() {
        // Given
        VerifyCodeCommand command = new VerifyCodeCommand("test@example.com", "123456", "REGISTER");
        when(repository.verifyAndMarkAsUsed("test@example.com:REGISTER", "123456")).thenReturn(false);
        VerificationCode expiredCode = VerificationCode.restore(
            "test@example.com:REGISTER", VerificationType.EMAIL, "test@example.com",
            "123456", java.time.Instant.now().minusSeconds(1), false, VerificationPurpose.REGISTER
        );
        when(repository.findById("test@example.com:REGISTER")).thenReturn(java.util.Optional.of(expiredCode));

        // When & Then
        assertThatThrownBy(() -> service.verifyCode(command))
            .isInstanceOf(DomainException.class)
            .hasMessageContaining(VerificationCodeError.CODE_EXPIRED.message());
    }

    // ========== SMS 验证码测试 ==========

    @Test
    void given_valid_phone_when_send_sms_code_then_success() {
        // Given
        when(properties.getExpireMinutes()).thenReturn(5);
        when(properties.getPhoneCooldownSeconds()).thenReturn(60L);
        when(properties.getIpMaxPerHour()).thenReturn(10);

        SendSmsCodeCommand command = new SendSmsCodeCommand("13800138000", "REGISTER", "captcha-id", "A1B2");
        when(generator.generate()).thenReturn("654321");
        when(repository.tryAcquirePhoneLock("13800138000", "REGISTER")).thenReturn(true);
        when(repository.checkAndIncrementIp("127.0.0.1")).thenReturn(1L);
        doNothing().when(captchaAppService).verifyCaptcha("captcha-id", "A1B2");

        // When
        SendCodeResponse response = service.sendSmsVerificationCode(command, "127.0.0.1");

        // Then
        assertThat(response.expireInSeconds()).isEqualTo(300);
        assertThat(response.resentAfterSeconds()).isEqualTo(60);
        verify(generator).generate();
        verify(repository).save(any(VerificationCode.class));
        verify(repository).tryAcquirePhoneLock("13800138000", "REGISTER");
        verify(repository).checkAndIncrementIp("127.0.0.1");
        verify(captchaAppService).verifyCaptcha("captcha-id", "A1B2");
        // bug#3 回归：发送端必须收到生成器产出的真实码（654321），而非硬编码 123456
        verify(messageSender).send("13800138000", "654321", VerificationPurpose.REGISTER);
    }

    @Test
    void given_invalid_phone_when_send_sms_code_then_throw_phone_invalid() {
        // Given
        SendSmsCodeCommand command = new SendSmsCodeCommand("invalid-phone", "REGISTER", "captcha-id", "A1B2");

        // When & Then
        assertThatThrownBy(() -> service.sendSmsVerificationCode(command, "127.0.0.1"))
            .isInstanceOf(DomainException.class)
            .hasMessageContaining(VerificationCodeError.PHONE_INVALID.message());
    }

    @Test
    void given_phone_rate_limited_when_send_sms_code_then_throw_rate_limit_phone() {
        // Given
        when(repository.tryAcquirePhoneLock("13800138000", "REGISTER")).thenReturn(false);
        doNothing().when(captchaAppService).verifyCaptcha("captcha-id", "A1B2");

        SendSmsCodeCommand command = new SendSmsCodeCommand("13800138000", "REGISTER", "captcha-id", "A1B2");

        // When & Then
        assertThatThrownBy(() -> service.sendSmsVerificationCode(command, "127.0.0.1"))
            .isInstanceOf(DomainException.class)
            .hasMessageContaining(VerificationCodeError.RATE_LIMIT_PHONE.message());
    }

    @Test
    void given_valid_sms_code_when_verify_phone_code_then_success() {
        // Given
        VerifySmsCodeCommand command = new VerifySmsCodeCommand("13800138000", "654321", "REGISTER");
        when(repository.verifyAndMarkAsUsed("13800138000:REGISTER", "654321")).thenReturn(true);

        // When
        VerifyCodeResult result = service.verifyPhoneCode(command);

        // Then
        assertThat(result.verified()).isTrue();
        verify(repository).verifyAndMarkAsUsed("13800138000:REGISTER", "654321");
    }

    @Test
    void given_wrong_code_when_verify_phone_code_then_throw_code_invalid() {
        // Given
        VerifySmsCodeCommand command = new VerifySmsCodeCommand("13800138000", "000000", "REGISTER");
        VerificationCode code = VerificationCode.create(
            VerificationType.SMS, "13800138000", "654321", VerificationPurpose.REGISTER);
        when(repository.verifyAndMarkAsUsed("13800138000:REGISTER", "000000")).thenReturn(false);
        when(repository.findById("13800138000:REGISTER")).thenReturn(java.util.Optional.of(code));

        // When & Then
        assertThatThrownBy(() -> service.verifyPhoneCode(command))
            .isInstanceOf(DomainException.class)
            .hasMessageContaining(VerificationCodeError.CODE_INVALID.message());
    }
}
