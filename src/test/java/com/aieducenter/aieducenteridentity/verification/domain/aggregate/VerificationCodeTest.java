package com.aieducenter.aieducenteridentity.verification.domain.aggregate;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import com.cartisan.core.exception.DomainException;
import com.aieducenter.aieducenteridentity.verification.domain.enums.VerificationPurpose;
import com.aieducenter.aieducenteridentity.verification.domain.enums.VerificationType;

import static org.assertj.core.api.Assertions.*;

class VerificationCodeTest {

    @Test
    void given_valid_input_when_create_verification_code_then_success() {
        // Given
        String email = "test@example.com";
        String code = "123456";
        VerificationPurpose purpose = VerificationPurpose.REGISTER;

        // When
        VerificationCode verificationCode = VerificationCode.create(
            VerificationType.EMAIL, email, code, purpose
        );

        // Then
        assertThat(verificationCode.getId()).isNotEmpty();
        assertThat(verificationCode.getType()).isEqualTo(VerificationType.EMAIL);
        assertThat(verificationCode.getTarget()).isEqualTo(email);
        assertThat(verificationCode.getCode()).isEqualTo(code);
        assertThat(verificationCode.getPurpose()).isEqualTo(purpose);
        assertThat(verificationCode.isUsed()).isFalse();
        assertThat(verificationCode.getExpireAt()).isAfter(Instant.now());
        assertThat(verificationCode.getCreatedAt()).isNotNull();
    }

    @Test
    void given_code_matches_and_not_expired_and_not_used_when_validate_then_return_true() {
        // Given
        VerificationCode verificationCode = VerificationCode.create(
            VerificationType.EMAIL, "test@example.com", "123456", VerificationPurpose.REGISTER
        );

        // When
        boolean valid = verificationCode.isValid("123456");

        // Then
        assertThat(valid).isTrue();
    }

    @Test
    void given_code_not_match_when_validate_then_return_false() {
        // Given
        VerificationCode verificationCode = VerificationCode.create(
            VerificationType.EMAIL, "test@example.com", "123456", VerificationPurpose.REGISTER
        );

        // When
        boolean valid = verificationCode.isValid("999999");

        // Then
        assertThat(valid).isFalse();
    }

    @Test
    void given_code_is_used_when_validate_then_return_false() {
        // Given
        VerificationCode verificationCode = VerificationCode.create(
            VerificationType.EMAIL, "test@example.com", "123456", VerificationPurpose.REGISTER
        );
        verificationCode.markAsUsed();

        // When
        boolean valid = verificationCode.isValid("123456");

        // Then
        assertThat(valid).isFalse();
    }

    @Test
    void given_code_not_used_when_mark_as_used_then_success() {
        // Given
        VerificationCode verificationCode = VerificationCode.create(
            VerificationType.EMAIL, "test@example.com", "123456", VerificationPurpose.REGISTER
        );

        // When
        verificationCode.markAsUsed();

        // Then
        assertThat(verificationCode.isUsed()).isTrue();
    }

    @Test
    void given_code_already_used_when_mark_as_used_then_throw_exception() {
        // Given
        VerificationCode verificationCode = VerificationCode.create(
            VerificationType.EMAIL, "test@example.com", "123456", VerificationPurpose.REGISTER
        );
        verificationCode.markAsUsed();

        // When & Then
        assertThatThrownBy(() -> verificationCode.markAsUsed())
            .isInstanceOf(DomainException.class);
    }

    @Test
    void given_verification_code_when_restore_then_purpose_is_preserved() {
        // Given
        String id = "test@example.com:REGISTER";
        VerificationPurpose expectedPurpose = VerificationPurpose.REGISTER;

        // When
        VerificationCode restored = VerificationCode.restore(
            id, VerificationType.EMAIL, "test@example.com", "123456",
            Instant.now().plusSeconds(300), false, expectedPurpose
        );

        // Then
        assertThat(restored.getPurpose()).isEqualTo(expectedPurpose);
    }

    @Test
    void given_id_matches_when_compare_identity_then_return_true() {
        // Given
        VerificationCode code1 = VerificationCode.create(
            VerificationType.EMAIL, "test@example.com", "123456", VerificationPurpose.REGISTER
        );
        VerificationCode code2 = VerificationCode.create(
            VerificationType.EMAIL, "test@example.com", "654321", VerificationPurpose.REGISTER
        );

        // When & Then
        assertThat(code1.sameIdentityAs(code2)).isTrue();
    }

    @Test
    void given_null_code_when_create_then_throw_exception() {
        assertThatThrownBy(() -> VerificationCode.create(
                VerificationType.EMAIL, "test@example.com", null, VerificationPurpose.REGISTER))
            .isInstanceOf(DomainException.class);
    }

    @Test
    void given_expired_code_when_validate_then_return_false() {
        // Given
        VerificationCode verificationCode = VerificationCode.restore(
            "test@example.com:REGISTER", VerificationType.EMAIL,
            "test@example.com", "123456",
            Instant.now().minusSeconds(1), false, VerificationPurpose.REGISTER
        );

        // When & Then
        assertThat(verificationCode.isValid("123456")).isFalse();
    }

    @Test
    void given_null_other_when_compare_identity_then_return_false() {
        // Given
        VerificationCode code = VerificationCode.create(
            VerificationType.EMAIL, "test@example.com", "123456", VerificationPurpose.REGISTER
        );

        // When & Then
        assertThat(code.sameIdentityAs(null)).isFalse();
    }

    @Test
    void given_different_target_when_compare_identity_then_return_false() {
        // Given
        VerificationCode code1 = VerificationCode.create(
            VerificationType.EMAIL, "a@example.com", "123456", VerificationPurpose.REGISTER
        );
        VerificationCode code2 = VerificationCode.create(
            VerificationType.EMAIL, "b@example.com", "123456", VerificationPurpose.REGISTER
        );

        // When & Then
        assertThat(code1.sameIdentityAs(code2)).isFalse();
    }
}
