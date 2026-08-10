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

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.aieducenter.aieducenteridentity.account.domain.aggregate.Account;
import com.aieducenter.aieducenteridentity.account.domain.enums.AccountStatus;
import com.aieducenter.aieducenteridentity.account.domain.error.AccountError;
import com.aieducenter.aieducenteridentity.account.domain.repository.AccountRepository;
import com.aieducenter.aieducenteridentity.sso.application.dto.LoginByCodeSsoCommand;
import com.aieducenter.aieducenteridentity.sso.application.dto.SsoLoginResult;
import com.aieducenter.aieducenteridentity.sso.domain.client.SsoClient;
import com.aieducenter.aieducenteridentity.sso.domain.client.SsoClientValidationService;
import com.aieducenter.aieducenteridentity.sso.domain.error.OidcException;
import com.aieducenter.aieducenteridentity.sso.domain.error.SsoError;
import com.aieducenter.aieducenteridentity.sso.infrastructure.verification.VerificationCodePort;
import com.aieducenter.aieducenteridentity.verification.domain.error.VerificationCodeError;
import com.cartisan.core.exception.DomainException;

class SsoLoginCodeAppServiceTest {

    private static final String CLIENT_ID = "demo-client";
    private static final String REDIRECT_URI = "https://demo.localhost/auth/callback";
    private static final String EMAIL = "user@aieducenter.com";
    private static final String PHONE = "13900100001";
    private static final String CODE = "123456";

    private final SsoClientValidationService clientValidation = mock(SsoClientValidationService.class);
    private final AccountRepository accountRepository = mock(AccountRepository.class);
    private final VerificationCodePort verificationCodePort = mock(VerificationCodePort.class);
    private final SsoLoginCompletionAppService loginCompletion = mock(SsoLoginCompletionAppService.class);

    private final SsoLoginCodeAppService service = new SsoLoginCodeAppService(clientValidation, accountRepository,
        verificationCodePort, loginCompletion);

    private final SsoClient client = new SsoClient(CLIENT_ID, "Demo", "hash",
        java.util.Set.of(REDIRECT_URI), java.util.Set.of(), java.util.Set.of("openid"), java.util.Set.of("authorization_code"), true);

    @BeforeEach
    void stubClient() {
        when(clientValidation.requireActiveClient(CLIENT_ID)).thenReturn(client);
    }

    private LoginByCodeSsoCommand emailCommand() {
        return new LoginByCodeSsoCommand(CLIENT_ID, REDIRECT_URI, "st", "non", "openid email", EMAIL, CODE);
    }

    private Account emailAccount(AccountStatus status, boolean locked) {
        return Account.restore(900L, EMAIL, null, "hash", status, locked, null);
    }

    @Test
    void given_valid_email_code_when_login_then_verify_with_login_purpose_and_delegate_to_completion() {
        Account account = emailAccount(AccountStatus.ACTIVE, false);
        when(accountRepository.findByEmail(EMAIL)).thenReturn(Optional.of(account));
        when(loginCompletion.completeLogin(account, client, REDIRECT_URI, "non", "openid email", "st"))
            .thenReturn(new SsoLoginResult("sess-1", REDIRECT_URI + "?code=ABC&state=st"));

        SsoLoginResult result = service.loginByCode(emailCommand());

        assertThat(result.sessionId()).isEqualTo("sess-1");
        assertThat(result.redirectUrl()).isEqualTo(REDIRECT_URI + "?code=ABC&state=st");
        verify(verificationCodePort).verifyCode(EMAIL, CODE, "LOGIN");
        verify(accountRepository).save(account);
    }

    @Test
    void given_valid_phone_code_when_login_then_verify_phone_code() {
        Account account = Account.restore(901L, null, PHONE, null, AccountStatus.ACTIVE, false, null);
        when(accountRepository.findByPhone(PHONE)).thenReturn(Optional.of(account));
        when(loginCompletion.completeLogin(eq(account), eq(client), eq(REDIRECT_URI), isNull(), isNull(), isNull()))
            .thenReturn(new SsoLoginResult("sess-2", REDIRECT_URI + "?code=XYZ"));

        SsoLoginResult result = service.loginByCode(
            new LoginByCodeSsoCommand(CLIENT_ID, REDIRECT_URI, null, null, null, PHONE, CODE));

        assertThat(result.sessionId()).isEqualTo("sess-2");
        verify(verificationCodePort).verifyPhoneCode(PHONE, CODE, "LOGIN");
    }

    @Test
    void given_wrong_code_when_login_then_code_invalid_and_no_session() {
        doThrow(new DomainException(VerificationCodeError.CODE_INVALID))
            .when(verificationCodePort).verifyCode(EMAIL, "000000", "LOGIN");

        assertThatThrownBy(() -> service.loginByCode(
            new LoginByCodeSsoCommand(CLIENT_ID, REDIRECT_URI, null, null, null, EMAIL, "000000")))
            .isInstanceOf(DomainException.class)
            .extracting(ex -> ((DomainException) ex).getCodeMessage())
            .isEqualTo(VerificationCodeError.CODE_INVALID);
        verify(loginCompletion, never()).completeLogin(any(), any(), any(), any(), any(), any());
    }

    /** 防枚举 AC：账号不存在与验证码错误同一响应（同一 code+message+status）。 */
    @Test
    void given_valid_code_but_unknown_account_when_login_then_same_response_as_wrong_code() {
        when(accountRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.loginByCode(emailCommand()))
            .isInstanceOf(DomainException.class)
            .extracting(ex -> ((DomainException) ex).getCodeMessage())
            .isEqualTo(VerificationCodeError.CODE_INVALID);
        verify(loginCompletion, never()).completeLogin(any(), any(), any(), any(), any(), any());
    }

    @Test
    void given_disabled_account_when_login_then_account_disabled() {
        Account account = emailAccount(AccountStatus.DISABLED, false);
        when(accountRepository.findByEmail(EMAIL)).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> service.loginByCode(emailCommand()))
            .isInstanceOf(DomainException.class)
            .extracting(ex -> ((DomainException) ex).getCodeMessage())
            .isEqualTo(AccountError.ACCOUNT_DISABLED);
    }

    @Test
    void given_unknown_client_when_login_then_unauthorized_client() {
        OidcException error = new OidcException(SsoError.UNAUTHORIZED_CLIENT, "client_id 无效或未注册");
        when(clientValidation.requireActiveClient("ghost")).thenThrow(error);

        assertThatThrownBy(() -> service.loginByCode(
            new LoginByCodeSsoCommand("ghost", REDIRECT_URI, null, null, null, EMAIL, CODE)))
            .isSameAs(error);
    }

    @Test
    void given_redirect_uri_not_whitelisted_when_login_then_invalid_request() {
        OidcException error = new OidcException(SsoError.INVALID_REQUEST, "redirect_uri 未登记");
        doThrow(error).when(clientValidation).requireRedirectUri(client, "https://evil.example/callback");

        assertThatThrownBy(() -> service.loginByCode(
            new LoginByCodeSsoCommand(CLIENT_ID, "https://evil.example/callback", null, null, null, EMAIL, CODE)))
            .isSameAs(error);
    }
}
