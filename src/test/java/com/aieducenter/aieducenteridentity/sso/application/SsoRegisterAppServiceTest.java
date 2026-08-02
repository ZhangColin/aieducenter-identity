package com.aieducenter.aieducenteridentity.sso.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.aieducenter.aieducenteridentity.account.domain.aggregate.Account;
import com.aieducenter.aieducenteridentity.account.domain.enums.AccountStatus;
import com.aieducenter.aieducenteridentity.account.domain.error.AccountError;
import com.aieducenter.aieducenteridentity.account.domain.repository.AccountRepository;
import com.aieducenter.aieducenteridentity.account.domain.service.AccountPasswordEncoderService;
import com.aieducenter.aieducenteridentity.sso.application.dto.RegisterByPasswordSsoCommand;
import com.aieducenter.aieducenteridentity.sso.application.dto.SsoLoginResult;
import com.aieducenter.aieducenteridentity.sso.domain.client.SsoClient;
import com.aieducenter.aieducenteridentity.sso.domain.client.SsoClientValidationService;
import com.aieducenter.aieducenteridentity.sso.domain.error.OidcException;
import com.aieducenter.aieducenteridentity.sso.domain.error.SsoError;
import com.aieducenter.aieducenteridentity.sso.domain.session.SsoSession;
import com.aieducenter.aieducenteridentity.sso.domain.session.SsoSessionRepository;
import com.cartisan.core.exception.DomainException;

class SsoRegisterAppServiceTest {

    private static final String CLIENT_ID = "demo-client";
    private static final String REDIRECT_URI = "https://demo.localhost/auth/callback";
    private static final String EMAIL = "new@aieducenter.com";
    private static final String PHONE = "13900140001";
    private static final String PASSWORD = "Password123";

    private final SsoClientValidationService clientValidation = mock(SsoClientValidationService.class);
    private final SsoSessionRepository sessionRepository = mock(SsoSessionRepository.class);
    private final AuthorizationCodeAppService codeService = mock(AuthorizationCodeAppService.class);
    private final AccountRepository accountRepository = mock(AccountRepository.class);
    private final AccountPasswordEncoderService passwordEncoderService = mock(AccountPasswordEncoderService.class);

    private final SsoRegisterAppService service = new SsoRegisterAppService(clientValidation, sessionRepository,
        codeService, accountRepository, passwordEncoderService);

    private final SsoClient client = new SsoClient(CLIENT_ID, "Demo", "hash",
        java.util.Set.of(REDIRECT_URI), java.util.Set.of("openid"), java.util.Set.of("authorization_code"), true);

    @BeforeEach
    void stubClient() {
        when(clientValidation.requireActiveClient(CLIENT_ID)).thenReturn(client);
    }

    @Test
    void given_new_email_when_register_then_create_account_session_and_code() {
        when(accountRepository.existsByEmail(EMAIL)).thenReturn(false);
        when(passwordEncoderService.encodePassword(PASSWORD)).thenReturn("hash");
        when(sessionRepository.create(any(), eq(EMAIL))).thenReturn(
            new SsoSession("sess-1", 1L, EMAIL, Instant.now(), Instant.now().plusSeconds(60)));
        when(codeService.issueCodeAndRedirect(any(), eq(client), eq(REDIRECT_URI), eq("non"), eq("openid email"), eq("sess-1"), eq("st")))
            .thenReturn(REDIRECT_URI + "?code=ABC&state=st");

        SsoLoginResult result = service.registerByPassword(
            new RegisterByPasswordSsoCommand(CLIENT_ID, REDIRECT_URI, "st", "non", "openid email", EMAIL, null, PASSWORD));

        assertThat(result.sessionId()).isEqualTo("sess-1");
        assertThat(result.redirectUrl()).isEqualTo(REDIRECT_URI + "?code=ABC&state=st");

        ArgumentCaptor<Account> saved = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).save(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo(EMAIL);
        assertThat(saved.getValue().getPhone()).isNull();
        assertThat(saved.getValue().getPasswordHash()).isEqualTo("hash");
        assertThat(saved.getValue().getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(saved.getValue().getLastLoginAt()).isNotNull();
    }

    @Test
    void given_existing_email_when_register_then_email_already_exists_and_no_second_account() {
        when(accountRepository.existsByEmail(EMAIL)).thenReturn(true);

        assertThatThrownBy(() -> service.registerByPassword(
            new RegisterByPasswordSsoCommand(CLIENT_ID, REDIRECT_URI, null, null, null, EMAIL, null, PASSWORD)))
            .isInstanceOf(DomainException.class)
            .extracting(ex -> ((DomainException) ex).getCodeMessage())
            .isEqualTo(AccountError.EMAIL_ALREADY_EXISTS);
        verify(accountRepository, never()).save(any());
    }

    @Test
    void given_existing_phone_when_register_then_phone_already_exists_and_no_second_account() {
        when(accountRepository.existsByPhone(PHONE)).thenReturn(true);

        assertThatThrownBy(() -> service.registerByPassword(
            new RegisterByPasswordSsoCommand(CLIENT_ID, REDIRECT_URI, null, null, null, null, PHONE, PASSWORD)))
            .isInstanceOf(DomainException.class)
            .extracting(ex -> ((DomainException) ex).getCodeMessage())
            .isEqualTo(AccountError.PHONE_ALREADY_EXISTS);
        verify(accountRepository, never()).save(any());
    }

    @Test
    void given_neither_email_nor_phone_when_register_then_contact_required() {
        assertThatThrownBy(() -> service.registerByPassword(
            new RegisterByPasswordSsoCommand(CLIENT_ID, REDIRECT_URI, null, null, null, null, "  ", PASSWORD)))
            .isInstanceOf(DomainException.class)
            .extracting(ex -> ((DomainException) ex).getCodeMessage())
            .isEqualTo(AccountError.CONTACT_REQUIRED);
        verify(accountRepository, never()).save(any());
    }

    @Test
    void given_new_phone_when_register_then_create_phone_account() {
        when(accountRepository.existsByPhone(PHONE)).thenReturn(false);
        when(passwordEncoderService.encodePassword(PASSWORD)).thenReturn("hash");
        when(sessionRepository.create(any(), eq(PHONE))).thenReturn(
            new SsoSession("sess-1", 1L, PHONE, Instant.now(), Instant.now().plusSeconds(60)));
        when(codeService.issueCodeAndRedirect(any(), eq(client), eq(REDIRECT_URI), any(), eq(null), eq("sess-1"), any()))
            .thenReturn(REDIRECT_URI + "?code=ABC");

        SsoLoginResult result = service.registerByPassword(
            new RegisterByPasswordSsoCommand(CLIENT_ID, REDIRECT_URI, null, null, null, null, PHONE, PASSWORD));

        assertThat(result.sessionId()).isEqualTo("sess-1");
        ArgumentCaptor<Account> saved = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).save(saved.capture());
        assertThat(saved.getValue().getPhone()).isEqualTo(PHONE);
        assertThat(saved.getValue().getEmail()).isNull();
    }

    @Test
    void given_unknown_client_when_register_then_unauthorized_client() {
        OidcException error = new OidcException(SsoError.UNAUTHORIZED_CLIENT, "client_id 无效或未注册");
        when(clientValidation.requireActiveClient("ghost")).thenThrow(error);

        assertThatThrownBy(() -> service.registerByPassword(
            new RegisterByPasswordSsoCommand("ghost", REDIRECT_URI, null, null, null, EMAIL, null, PASSWORD)))
            .isSameAs(error);
        verify(accountRepository, never()).save(any());
    }

    @Test
    void given_redirect_uri_not_whitelisted_when_register_then_invalid_request() {
        OidcException error = new OidcException(SsoError.INVALID_REQUEST, "redirect_uri 未登记");
        org.mockito.Mockito.doThrow(error).when(clientValidation)
            .requireRedirectUri(client, "https://evil.example/callback");

        assertThatThrownBy(() -> service.registerByPassword(
            new RegisterByPasswordSsoCommand(CLIENT_ID, "https://evil.example/callback", null, null, null, EMAIL, null, PASSWORD)))
            .isSameAs(error);
        verify(accountRepository, never()).save(any());
    }
}
