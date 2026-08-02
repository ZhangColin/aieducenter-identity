package com.aieducenter.aieducenteridentity.sso.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.aieducenter.aieducenteridentity.account.domain.aggregate.Account;
import com.aieducenter.aieducenteridentity.account.domain.enums.AccountStatus;
import com.aieducenter.aieducenteridentity.account.domain.error.AccountError;
import com.aieducenter.aieducenteridentity.account.domain.repository.AccountRepository;
import com.aieducenter.aieducenteridentity.account.domain.service.AccountPasswordEncoderService;
import com.aieducenter.aieducenteridentity.sso.application.dto.LoginByPasswordSsoCommand;
import com.aieducenter.aieducenteridentity.sso.application.dto.SsoLoginResult;
import com.aieducenter.aieducenteridentity.sso.domain.client.SsoClient;
import com.aieducenter.aieducenteridentity.sso.domain.client.SsoClientValidationService;
import com.aieducenter.aieducenteridentity.sso.domain.error.OidcException;
import com.aieducenter.aieducenteridentity.sso.domain.error.SsoError;
import com.cartisan.core.exception.DomainException;

class SsoLoginAppServiceTest {

    private static final String CLIENT_ID = "demo-client";
    private static final String REDIRECT_URI = "https://demo.localhost/auth/callback";
    private static final String ACCOUNT = "13900100001";
    private static final String PASSWORD = "Password123";

    private final SsoClientValidationService clientValidation = mock(SsoClientValidationService.class);
    private final AccountRepository accountRepository = mock(AccountRepository.class);
    private final AccountPasswordEncoderService passwordEncoderService = mock(AccountPasswordEncoderService.class);
    private final SsoLoginCompletionAppService loginCompletion = mock(SsoLoginCompletionAppService.class);

    private final SsoLoginAppService service = new SsoLoginAppService(clientValidation, accountRepository,
        passwordEncoderService, loginCompletion);

    private final SsoClient client = new SsoClient(CLIENT_ID, "Demo", "hash",
        java.util.Set.of(REDIRECT_URI), java.util.Set.of("openid"), java.util.Set.of("authorization_code"), true);

    @BeforeEach
    void stubClient() {
        when(clientValidation.requireActiveClient(CLIENT_ID)).thenReturn(client);
    }

    private LoginByPasswordSsoCommand command() {
        return new LoginByPasswordSsoCommand(CLIENT_ID, REDIRECT_URI, "st", "non@ce", "openid profile", ACCOUNT, PASSWORD);
    }

    private Account account(AccountStatus status, boolean locked) {
        return Account.restore(900L, null, ACCOUNT, "hash", status, locked, null);
    }

    @Test
    void given_valid_credentials_when_login_then_delegate_to_completion() {
        Account account = account(AccountStatus.ACTIVE, false);
        when(accountRepository.findByEmail(ACCOUNT)).thenReturn(Optional.empty());
        when(accountRepository.findByPhone(ACCOUNT)).thenReturn(Optional.of(account));
        when(passwordEncoderService.verifyPassword(PASSWORD, "hash")).thenReturn(true);
        when(loginCompletion.completeLogin(account, client, REDIRECT_URI, "non@ce", "openid profile", "st"))
            .thenReturn(new SsoLoginResult("sess-1", REDIRECT_URI + "?code=ABC&state=st"));

        SsoLoginResult result = service.loginByPassword(command());

        assertThat(result.sessionId()).isEqualTo("sess-1");
        assertThat(result.redirectUrl()).isEqualTo(REDIRECT_URI + "?code=ABC&state=st");
        verify(accountRepository).save(account);
        verify(loginCompletion).completeLogin(account, client, REDIRECT_URI, "non@ce", "openid profile", "st");
    }

    @Test
    void given_unknown_account_when_login_then_login_password_incorrect() {
        when(accountRepository.findByEmail(ACCOUNT)).thenReturn(Optional.empty());
        when(accountRepository.findByPhone(ACCOUNT)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.loginByPassword(command()))
            .isInstanceOf(DomainException.class)
            .extracting(ex -> ((DomainException) ex).getCodeMessage())
            .isEqualTo(AccountError.LOGIN_PASSWORD_INCORRECT);
    }

    @Test
    void given_wrong_password_when_login_then_same_error_as_unknown_account() {
        Account account = account(AccountStatus.ACTIVE, false);
        when(accountRepository.findByEmail(ACCOUNT)).thenReturn(Optional.empty());
        when(accountRepository.findByPhone(ACCOUNT)).thenReturn(Optional.of(account));
        when(passwordEncoderService.verifyPassword(PASSWORD, "hash")).thenReturn(false);

        assertThatThrownBy(() -> service.loginByPassword(command()))
            .isInstanceOf(DomainException.class)
            .extracting(ex -> ((DomainException) ex).getCodeMessage())
            .isEqualTo(AccountError.LOGIN_PASSWORD_INCORRECT);
    }

    @Test
    void given_passwordless_account_when_login_by_password_then_login_password_incorrect() {
        Account account = Account.restore(900L, null, ACCOUNT, null, AccountStatus.ACTIVE, false, null);
        when(accountRepository.findByEmail(ACCOUNT)).thenReturn(Optional.empty());
        when(accountRepository.findByPhone(ACCOUNT)).thenReturn(Optional.of(account));
        when(passwordEncoderService.verifyPassword(eq(PASSWORD), isNull())).thenReturn(false);

        assertThatThrownBy(() -> service.loginByPassword(command()))
            .isInstanceOf(DomainException.class)
            .extracting(ex -> ((DomainException) ex).getCodeMessage())
            .isEqualTo(AccountError.LOGIN_PASSWORD_INCORRECT);
    }

    @Test
    void given_disabled_account_when_login_then_account_disabled() {
        Account account = account(AccountStatus.DISABLED, false);
        when(accountRepository.findByEmail(ACCOUNT)).thenReturn(Optional.empty());
        when(accountRepository.findByPhone(ACCOUNT)).thenReturn(Optional.of(account));
        when(passwordEncoderService.verifyPassword(PASSWORD, "hash")).thenReturn(true);

        assertThatThrownBy(() -> service.loginByPassword(command()))
            .isInstanceOf(DomainException.class)
            .extracting(ex -> ((DomainException) ex).getCodeMessage())
            .isEqualTo(AccountError.ACCOUNT_DISABLED);
    }

    @Test
    void given_unknown_client_when_login_then_unauthorized_client() {
        OidcException error = new OidcException(SsoError.UNAUTHORIZED_CLIENT, "client_id 无效或未注册");
        when(clientValidation.requireActiveClient("ghost")).thenThrow(error);

        assertThatThrownBy(() -> service.loginByPassword(
            new LoginByPasswordSsoCommand("ghost", REDIRECT_URI, null, null, null, ACCOUNT, PASSWORD)))
            .isSameAs(error);
    }

    @Test
    void given_redirect_uri_not_whitelisted_when_login_then_invalid_request() {
        OidcException error = new OidcException(SsoError.INVALID_REQUEST, "redirect_uri 未登记");
        org.mockito.Mockito.doThrow(error).when(clientValidation)
            .requireRedirectUri(client, "https://evil.example/callback");

        assertThatThrownBy(() -> service.loginByPassword(
            new LoginByPasswordSsoCommand(CLIENT_ID, "https://evil.example/callback", null, null, null, ACCOUNT, PASSWORD)))
            .isSameAs(error);
    }
}
