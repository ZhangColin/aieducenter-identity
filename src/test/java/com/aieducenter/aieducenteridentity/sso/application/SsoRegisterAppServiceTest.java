package com.aieducenter.aieducenteridentity.sso.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.aieducenter.aieducenteridentity.account.domain.aggregate.Account;
import com.aieducenter.aieducenteridentity.account.domain.enums.AccountStatus;
import com.aieducenter.aieducenteridentity.account.domain.error.AccountError;
import com.aieducenter.aieducenteridentity.account.domain.repository.AccountRepository;
import com.aieducenter.aieducenteridentity.account.domain.service.AccountPasswordEncoderService;
import com.aieducenter.aieducenteridentity.sso.application.dto.RegisterSsoCommand;
import com.aieducenter.aieducenteridentity.sso.application.dto.SsoLoginResult;
import com.aieducenter.aieducenteridentity.sso.domain.client.SsoClient;
import com.aieducenter.aieducenteridentity.sso.domain.client.SsoClientValidationService;
import com.aieducenter.aieducenteridentity.sso.domain.error.OidcException;
import com.aieducenter.aieducenteridentity.sso.domain.error.SsoError;
import com.aieducenter.aieducenteridentity.sso.infrastructure.verification.VerificationCodePort;
import com.aieducenter.aieducenteridentity.verification.domain.error.VerificationCodeError;
import com.cartisan.core.exception.DomainException;

class SsoRegisterAppServiceTest {

    private static final String CLIENT_ID = "demo-client";
    private static final String REDIRECT_URI = "https://demo.localhost/auth/callback";
    private static final String EMAIL = "new@aieducenter.com";
    private static final String PHONE = "13900140001";
    private static final String PASSWORD = "Password123";
    private static final String EMAIL_CODE = "111111";
    private static final String PHONE_CODE = "222222";

    private final SsoClientValidationService clientValidation = mock(SsoClientValidationService.class);
    private final AccountRepository accountRepository = mock(AccountRepository.class);
    private final AccountPasswordEncoderService passwordEncoderService = mock(AccountPasswordEncoderService.class);
    private final VerificationCodePort verificationCodePort = mock(VerificationCodePort.class);
    private final SsoLoginCompletionAppService loginCompletion = mock(SsoLoginCompletionAppService.class);

    private final SsoRegisterAppService service = new SsoRegisterAppService(clientValidation, accountRepository,
        passwordEncoderService, verificationCodePort, loginCompletion);

    private final SsoClient client = new SsoClient(CLIENT_ID, "Demo", "hash",
        java.util.Set.of(REDIRECT_URI), java.util.Set.of("openid"), java.util.Set.of("authorization_code"), true);

    @BeforeEach
    void stubClient() {
        when(clientValidation.requireActiveClient(CLIENT_ID)).thenReturn(client);
    }

    private RegisterSsoCommand emailCommand(String emailCode, String password) {
        return new RegisterSsoCommand(CLIENT_ID, REDIRECT_URI, "st", "non", "openid email",
            EMAIL, null, emailCode, null, password);
    }

    @Test
    void given_new_email_with_valid_code_when_register_then_create_account_and_delegate_to_completion() {
        when(accountRepository.existsByEmail(EMAIL)).thenReturn(false);
        when(passwordEncoderService.encodePassword(PASSWORD)).thenReturn("hash");
        when(loginCompletion.completeLogin(any(), eq(client), eq(REDIRECT_URI), eq("non"), eq("openid email"), eq("st")))
            .thenReturn(new SsoLoginResult("sess-1", REDIRECT_URI + "?code=ABC&state=st"));

        SsoLoginResult result = service.register(emailCommand(EMAIL_CODE, PASSWORD));

        assertThat(result.sessionId()).isEqualTo("sess-1");
        assertThat(result.redirectUrl()).isEqualTo(REDIRECT_URI + "?code=ABC&state=st");

        // 当场验码：REGISTER 用途
        verify(verificationCodePort).verifyCode(EMAIL, EMAIL_CODE, "REGISTER");

        ArgumentCaptor<Account> saved = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).save(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo(EMAIL);
        assertThat(saved.getValue().getPhone()).isNull();
        assertThat(saved.getValue().getPasswordHash()).isEqualTo("hash");
        assertThat(saved.getValue().getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(saved.getValue().getLastLoginAt()).isNotNull();
    }

    @Test
    void given_wrong_email_code_when_register_then_code_invalid_and_no_account() {
        when(accountRepository.existsByEmail(EMAIL)).thenReturn(false);
        doThrow(new DomainException(VerificationCodeError.CODE_INVALID))
            .when(verificationCodePort).verifyCode(EMAIL, "000000", "REGISTER");

        assertThatThrownBy(() -> service.register(emailCommand("000000", PASSWORD)))
            .isInstanceOf(DomainException.class)
            .extracting(ex -> ((DomainException) ex).getCodeMessage())
            .isEqualTo(VerificationCodeError.CODE_INVALID);
        // 验不过不建号
        verify(accountRepository, never()).save(any());
    }

    @Test
    void given_email_without_code_when_register_then_code_invalid_and_no_account() {
        when(accountRepository.existsByEmail(EMAIL)).thenReturn(false);

        assertThatThrownBy(() -> service.register(emailCommand(null, PASSWORD)))
            .isInstanceOf(DomainException.class)
            .extracting(ex -> ((DomainException) ex).getCodeMessage())
            .isEqualTo(VerificationCodeError.CODE_INVALID);
        verify(accountRepository, never()).save(any());
        verify(verificationCodePort, never()).verifyCode(any(), any(), any());
    }

    @Test
    void given_no_password_when_register_then_account_without_password_hash() {
        when(accountRepository.existsByEmail(EMAIL)).thenReturn(false);
        when(loginCompletion.completeLogin(any(), eq(client), eq(REDIRECT_URI), any(), any(), any()))
            .thenReturn(new SsoLoginResult("sess-1", REDIRECT_URI + "?code=ABC"));

        SsoLoginResult result = service.register(emailCommand(EMAIL_CODE, null));

        assertThat(result.sessionId()).isEqualTo("sess-1");
        ArgumentCaptor<Account> saved = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).save(saved.capture());
        assertThat(saved.getValue().getPasswordHash()).isNull();
        verify(passwordEncoderService, never()).encodePassword(any());
    }

    @Test
    void given_both_contacts_when_register_then_both_codes_verified() {
        when(accountRepository.existsByEmail(EMAIL)).thenReturn(false);
        when(accountRepository.existsByPhone(PHONE)).thenReturn(false);
        when(passwordEncoderService.encodePassword(PASSWORD)).thenReturn("hash");
        when(loginCompletion.completeLogin(any(), eq(client), eq(REDIRECT_URI), any(), any(), any()))
            .thenReturn(new SsoLoginResult("sess-1", REDIRECT_URI + "?code=ABC"));

        service.register(new RegisterSsoCommand(CLIENT_ID, REDIRECT_URI, null, null, null,
            EMAIL, PHONE, EMAIL_CODE, PHONE_CODE, PASSWORD));

        verify(verificationCodePort).verifyCode(EMAIL, EMAIL_CODE, "REGISTER");
        verify(verificationCodePort).verifyPhoneCode(PHONE, PHONE_CODE, "REGISTER");
        ArgumentCaptor<Account> saved = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).save(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo(EMAIL);
        assertThat(saved.getValue().getPhone()).isEqualTo(PHONE);
    }

    @Test
    void given_both_contacts_but_phone_code_wrong_when_register_then_no_account() {
        when(accountRepository.existsByEmail(EMAIL)).thenReturn(false);
        when(accountRepository.existsByPhone(PHONE)).thenReturn(false);
        doThrow(new DomainException(VerificationCodeError.CODE_INVALID))
            .when(verificationCodePort).verifyPhoneCode(PHONE, "000000", "REGISTER");

        assertThatThrownBy(() -> service.register(new RegisterSsoCommand(CLIENT_ID, REDIRECT_URI, null, null, null,
            EMAIL, PHONE, EMAIL_CODE, "000000", PASSWORD)))
            .isInstanceOf(DomainException.class)
            .extracting(ex -> ((DomainException) ex).getCodeMessage())
            .isEqualTo(VerificationCodeError.CODE_INVALID);
        verify(accountRepository, never()).save(any());
    }

    @Test
    void given_existing_email_when_register_then_email_already_exists_and_no_verify_no_account() {
        when(accountRepository.existsByEmail(EMAIL)).thenReturn(true);

        assertThatThrownBy(() -> service.register(emailCommand(EMAIL_CODE, PASSWORD)))
            .isInstanceOf(DomainException.class)
            .extracting(ex -> ((DomainException) ex).getCodeMessage())
            .isEqualTo(AccountError.EMAIL_ALREADY_EXISTS);
        verify(accountRepository, never()).save(any());
        // 已存在直接 409——不消耗验证码
        verify(verificationCodePort, never()).verifyCode(any(), any(), any());
    }

    @Test
    void given_existing_phone_when_register_then_phone_already_exists_and_no_second_account() {
        when(accountRepository.existsByPhone(PHONE)).thenReturn(true);

        assertThatThrownBy(() -> service.register(new RegisterSsoCommand(CLIENT_ID, REDIRECT_URI, null, null, null,
            null, PHONE, null, PHONE_CODE, PASSWORD)))
            .isInstanceOf(DomainException.class)
            .extracting(ex -> ((DomainException) ex).getCodeMessage())
            .isEqualTo(AccountError.PHONE_ALREADY_EXISTS);
        verify(accountRepository, never()).save(any());
    }

    @Test
    void given_neither_email_nor_phone_when_register_then_contact_required() {
        assertThatThrownBy(() -> service.register(new RegisterSsoCommand(CLIENT_ID, REDIRECT_URI, null, null, null,
            null, "  ", null, null, PASSWORD)))
            .isInstanceOf(DomainException.class)
            .extracting(ex -> ((DomainException) ex).getCodeMessage())
            .isEqualTo(AccountError.CONTACT_REQUIRED);
        verify(accountRepository, never()).save(any());
    }

    @Test
    void given_new_phone_with_valid_code_when_register_then_create_phone_account() {
        when(accountRepository.existsByPhone(PHONE)).thenReturn(false);
        when(passwordEncoderService.encodePassword(PASSWORD)).thenReturn("hash");
        when(loginCompletion.completeLogin(any(), eq(client), eq(REDIRECT_URI), isNull(), isNull(), isNull()))
            .thenReturn(new SsoLoginResult("sess-1", REDIRECT_URI + "?code=ABC"));

        SsoLoginResult result = service.register(new RegisterSsoCommand(CLIENT_ID, REDIRECT_URI, null, null, null,
            null, PHONE, null, PHONE_CODE, PASSWORD));

        assertThat(result.sessionId()).isEqualTo("sess-1");
        verify(verificationCodePort).verifyPhoneCode(PHONE, PHONE_CODE, "REGISTER");
        ArgumentCaptor<Account> saved = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).save(saved.capture());
        assertThat(saved.getValue().getPhone()).isEqualTo(PHONE);
        assertThat(saved.getValue().getEmail()).isNull();
    }

    @Test
    void given_unknown_client_when_register_then_unauthorized_client() {
        OidcException error = new OidcException(SsoError.UNAUTHORIZED_CLIENT, "client_id 无效或未注册");
        when(clientValidation.requireActiveClient("ghost")).thenThrow(error);

        assertThatThrownBy(() -> service.register(new RegisterSsoCommand("ghost", REDIRECT_URI, null, null, null,
            EMAIL, null, EMAIL_CODE, null, PASSWORD)))
            .isSameAs(error);
        verify(accountRepository, never()).save(any());
    }

    @Test
    void given_redirect_uri_not_whitelisted_when_register_then_invalid_request() {
        OidcException error = new OidcException(SsoError.INVALID_REQUEST, "redirect_uri 未登记");
        doThrow(error).when(clientValidation).requireRedirectUri(client, "https://evil.example/callback");

        assertThatThrownBy(() -> service.register(new RegisterSsoCommand(CLIENT_ID, "https://evil.example/callback",
            null, null, null, EMAIL, null, EMAIL_CODE, null, PASSWORD)))
            .isSameAs(error);
        verify(accountRepository, never()).save(any());
    }
}
