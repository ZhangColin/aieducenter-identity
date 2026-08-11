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

import com.aieducenter.aieducenteridentity.account.application.AccountAuthAppService;
import com.aieducenter.aieducenteridentity.account.application.dto.command.RegisterAccountCommand;
import com.aieducenter.aieducenteridentity.account.application.dto.response.SubjectStatus;
import com.aieducenter.aieducenteridentity.account.application.dto.response.SubjectView;
import com.aieducenter.aieducenteridentity.account.domain.error.AccountError;
import com.aieducenter.aieducenteridentity.sso.application.dto.RegisterSsoCommand;
import com.aieducenter.aieducenteridentity.sso.application.dto.SsoLoginResult;
import com.aieducenter.aieducenteridentity.sso.domain.client.SsoClient;
import com.aieducenter.aieducenteridentity.sso.domain.client.SsoClientValidationService;
import com.aieducenter.aieducenteridentity.sso.domain.error.OidcException;
import com.aieducenter.aieducenteridentity.sso.domain.error.SsoError;
import com.aieducenter.aieducenteridentity.sso.infrastructure.verification.VerificationCodePort;
import com.aieducenter.aieducenteridentity.verification.domain.error.VerificationCodeError;
import com.cartisan.core.exception.DomainException;

/**
 * sso 注册经 {@link AccountAuthAppService#register} 委托——唯一性 / 密码可选 encode / 聚合不变量收在 account 应用层。
 *
 * <p>顺序：先当场验码（REGISTER，验不过不建号）→ 再 {@code register}（内部做唯一性）。
 * 故重复联络方式会先消耗验证码再抛 {@code EMAIL_ALREADY_EXISTS}/{@code PHONE_ALREADY_EXISTS}——
 * 反而收紧防枚举（未验码者拿不到存在性信息）。</p>
 */
class SsoRegisterAppServiceTest {

    private static final String CLIENT_ID = "demo-client";
    private static final String REDIRECT_URI = "https://demo.localhost/auth/callback";
    private static final String EMAIL = "new@aieducenter.com";
    private static final String PHONE = "13900140001";
    private static final String PASSWORD = "Password123";
    private static final String EMAIL_CODE = "111111";
    private static final String PHONE_CODE = "222222";

    private final SsoClientValidationService clientValidation = mock(SsoClientValidationService.class);
    private final AccountAuthAppService accountAuth = mock(AccountAuthAppService.class);
    private final VerificationCodePort verificationCodePort = mock(VerificationCodePort.class);
    private final SsoLoginCompletionAppService loginCompletion = mock(SsoLoginCompletionAppService.class);

    private final SsoRegisterAppService service = new SsoRegisterAppService(clientValidation, accountAuth,
        verificationCodePort, loginCompletion);

    private final SsoClient client = new SsoClient(CLIENT_ID, "Demo", "hash",
        java.util.Set.of(REDIRECT_URI), java.util.Set.of(), java.util.Set.of("openid"), java.util.Set.of("authorization_code"), true);

    @BeforeEach
    void stubClient() {
        when(clientValidation.requireActiveClient(CLIENT_ID)).thenReturn(client);
    }

    private RegisterSsoCommand emailCommand(String emailCode, String password) {
        return new RegisterSsoCommand(CLIENT_ID, REDIRECT_URI, "st", "non", "openid email",
            EMAIL, null, emailCode, null, password);
    }

    private SubjectView emailSubject() {
        return new SubjectView(900L, EMAIL, null, null, null, SubjectStatus.USABLE);
    }

    @Test
    void given_new_email_with_valid_code_when_register_then_verify_and_register_and_delegate() {
        SubjectView subject = emailSubject();
        when(accountAuth.register(any(RegisterAccountCommand.class))).thenReturn(subject);
        when(loginCompletion.completeLogin(eq(subject), eq(client), eq(REDIRECT_URI), eq("non"), eq("openid email"), eq("st")))
            .thenReturn(new SsoLoginResult("sess-1", REDIRECT_URI + "?code=ABC&state=st"));

        SsoLoginResult result = service.register(emailCommand(EMAIL_CODE, PASSWORD));

        assertThat(result.sessionId()).isEqualTo("sess-1");
        assertThat(result.redirectUrl()).isEqualTo(REDIRECT_URI + "?code=ABC&state=st");
        verify(verificationCodePort).verifyCode(EMAIL, EMAIL_CODE, "REGISTER");
        ArgumentCaptor<RegisterAccountCommand> cmd = ArgumentCaptor.forClass(RegisterAccountCommand.class);
        verify(accountAuth).register(cmd.capture());
        assertThat(cmd.getValue().email()).isEqualTo(EMAIL);
        assertThat(cmd.getValue().phone()).isNull();
        assertThat(cmd.getValue().password()).isEqualTo(PASSWORD);
    }

    @Test
    void given_wrong_email_code_when_register_then_code_invalid_and_no_register() {
        doThrow(new DomainException(VerificationCodeError.CODE_INVALID))
            .when(verificationCodePort).verifyCode(EMAIL, "000000", "REGISTER");

        assertThatThrownBy(() -> service.register(emailCommand("000000", PASSWORD)))
            .isInstanceOf(DomainException.class)
            .extracting(ex -> ((DomainException) ex).getCodeMessage())
            .isEqualTo(VerificationCodeError.CODE_INVALID);
        verify(accountAuth, never()).register(any());
    }

    @Test
    void given_email_without_code_when_register_then_code_invalid_and_no_verify_no_register() {
        assertThatThrownBy(() -> service.register(emailCommand(null, PASSWORD)))
            .isInstanceOf(DomainException.class)
            .extracting(ex -> ((DomainException) ex).getCodeMessage())
            .isEqualTo(VerificationCodeError.CODE_INVALID);
        verify(verificationCodePort, never()).verifyCode(any(), any(), any());
        verify(accountAuth, never()).register(any());
    }

    /** 密码可选收在 accountAuth——sso 把 null 原样透传给 register。 */
    @Test
    void given_no_password_when_register_then_register_called_with_null_password() {
        SubjectView subject = emailSubject();
        when(accountAuth.register(any(RegisterAccountCommand.class))).thenReturn(subject);
        when(loginCompletion.completeLogin(eq(subject), eq(client), eq(REDIRECT_URI), any(), any(), any()))
            .thenReturn(new SsoLoginResult("sess-1", REDIRECT_URI + "?code=ABC"));

        SsoLoginResult result = service.register(emailCommand(EMAIL_CODE, null));

        assertThat(result.sessionId()).isEqualTo("sess-1");
        ArgumentCaptor<RegisterAccountCommand> cmd = ArgumentCaptor.forClass(RegisterAccountCommand.class);
        verify(accountAuth).register(cmd.capture());
        assertThat(cmd.getValue().password()).isNull();
    }

    @Test
    void given_both_contacts_when_register_then_both_codes_verified_and_register_with_both() {
        SubjectView subject = new SubjectView(900L, EMAIL, PHONE, null, null, SubjectStatus.USABLE);
        when(accountAuth.register(any(RegisterAccountCommand.class))).thenReturn(subject);
        when(loginCompletion.completeLogin(eq(subject), eq(client), eq(REDIRECT_URI), any(), any(), any()))
            .thenReturn(new SsoLoginResult("sess-1", REDIRECT_URI + "?code=ABC"));

        service.register(new RegisterSsoCommand(CLIENT_ID, REDIRECT_URI, null, null, null,
            EMAIL, PHONE, EMAIL_CODE, PHONE_CODE, PASSWORD));

        verify(verificationCodePort).verifyCode(EMAIL, EMAIL_CODE, "REGISTER");
        verify(verificationCodePort).verifyPhoneCode(PHONE, PHONE_CODE, "REGISTER");
        ArgumentCaptor<RegisterAccountCommand> cmd = ArgumentCaptor.forClass(RegisterAccountCommand.class);
        verify(accountAuth).register(cmd.capture());
        assertThat(cmd.getValue().email()).isEqualTo(EMAIL);
        assertThat(cmd.getValue().phone()).isEqualTo(PHONE);
    }

    @Test
    void given_both_contacts_but_phone_code_wrong_when_register_then_no_register() {
        doThrow(new DomainException(VerificationCodeError.CODE_INVALID))
            .when(verificationCodePort).verifyPhoneCode(PHONE, "000000", "REGISTER");

        assertThatThrownBy(() -> service.register(new RegisterSsoCommand(CLIENT_ID, REDIRECT_URI, null, null, null,
            EMAIL, PHONE, EMAIL_CODE, "000000", PASSWORD)))
            .isInstanceOf(DomainException.class)
            .extracting(ex -> ((DomainException) ex).getCodeMessage())
            .isEqualTo(VerificationCodeError.CODE_INVALID);
        verify(accountAuth, never()).register(any());
    }

    /**
     * 唯一性收在 register 内、<b>验码之后</b>——故验码被调用，再抛 EMAIL_ALREADY_EXISTS（收紧防枚举：
     * 未验码者拿不到存在性信息）。
     */
    @Test
    void given_existing_email_when_register_then_email_already_exists_after_verify() {
        doThrow(new DomainException(AccountError.EMAIL_ALREADY_EXISTS))
            .when(accountAuth).register(any(RegisterAccountCommand.class));

        assertThatThrownBy(() -> service.register(emailCommand(EMAIL_CODE, PASSWORD)))
            .isInstanceOf(DomainException.class)
            .extracting(ex -> ((DomainException) ex).getCodeMessage())
            .isEqualTo(AccountError.EMAIL_ALREADY_EXISTS);
        verify(verificationCodePort).verifyCode(EMAIL, EMAIL_CODE, "REGISTER");
        verify(accountAuth).register(any(RegisterAccountCommand.class));
    }

    @Test
    void given_existing_phone_when_register_then_phone_already_exists_after_verify() {
        doThrow(new DomainException(AccountError.PHONE_ALREADY_EXISTS))
            .when(accountAuth).register(any(RegisterAccountCommand.class));

        assertThatThrownBy(() -> service.register(new RegisterSsoCommand(CLIENT_ID, REDIRECT_URI, null, null, null,
            null, PHONE, null, PHONE_CODE, PASSWORD)))
            .isInstanceOf(DomainException.class)
            .extracting(ex -> ((DomainException) ex).getCodeMessage())
            .isEqualTo(AccountError.PHONE_ALREADY_EXISTS);
        verify(verificationCodePort).verifyPhoneCode(PHONE, PHONE_CODE, "REGISTER");
        verify(accountAuth).register(any(RegisterAccountCommand.class));
    }

    /** 两个联络方式都空——sso 不验码，register 抛 CONTACT_REQUIRED（聚合不变量经 accountAuth 透出）。 */
    @Test
    void given_neither_email_nor_phone_when_register_then_contact_required_from_register() {
        doThrow(new DomainException(AccountError.CONTACT_REQUIRED))
            .when(accountAuth).register(any(RegisterAccountCommand.class));

        assertThatThrownBy(() -> service.register(new RegisterSsoCommand(CLIENT_ID, REDIRECT_URI, null, null, null,
            null, "  ", null, null, PASSWORD)))
            .isInstanceOf(DomainException.class)
            .extracting(ex -> ((DomainException) ex).getCodeMessage())
            .isEqualTo(AccountError.CONTACT_REQUIRED);
        verify(verificationCodePort, never()).verifyCode(any(), any(), any());
        verify(verificationCodePort, never()).verifyPhoneCode(any(), any(), any());
        verify(accountAuth).register(any(RegisterAccountCommand.class));
    }

    @Test
    void given_new_phone_with_valid_code_when_register_then_verify_phone_and_register() {
        SubjectView subject = new SubjectView(900L, null, PHONE, null, null, SubjectStatus.USABLE);
        when(accountAuth.register(any(RegisterAccountCommand.class))).thenReturn(subject);
        when(loginCompletion.completeLogin(eq(subject), eq(client), eq(REDIRECT_URI), isNull(), isNull(), isNull()))
            .thenReturn(new SsoLoginResult("sess-1", REDIRECT_URI + "?code=ABC"));

        SsoLoginResult result = service.register(new RegisterSsoCommand(CLIENT_ID, REDIRECT_URI, null, null, null,
            null, PHONE, null, PHONE_CODE, PASSWORD));

        assertThat(result.sessionId()).isEqualTo("sess-1");
        verify(verificationCodePort).verifyPhoneCode(PHONE, PHONE_CODE, "REGISTER");
        ArgumentCaptor<RegisterAccountCommand> cmd = ArgumentCaptor.forClass(RegisterAccountCommand.class);
        verify(accountAuth).register(cmd.capture());
        assertThat(cmd.getValue().phone()).isEqualTo(PHONE);
        assertThat(cmd.getValue().email()).isNull();
    }

    @Test
    void given_unknown_client_when_register_then_unauthorized_client_and_no_register() {
        OidcException error = new OidcException(SsoError.UNAUTHORIZED_CLIENT, "client_id 无效或未注册");
        when(clientValidation.requireActiveClient("ghost")).thenThrow(error);

        assertThatThrownBy(() -> service.register(new RegisterSsoCommand("ghost", REDIRECT_URI, null, null, null,
            EMAIL, null, EMAIL_CODE, null, PASSWORD)))
            .isSameAs(error);
        verify(accountAuth, never()).register(any());
    }

    @Test
    void given_redirect_uri_not_whitelisted_when_register_then_invalid_request_and_no_register() {
        OidcException error = new OidcException(SsoError.INVALID_REQUEST, "redirect_uri 未登记");
        doThrow(error).when(clientValidation).requireRedirectUri(eq(client), eq("https://evil.example/callback"));

        assertThatThrownBy(() -> service.register(new RegisterSsoCommand(CLIENT_ID, "https://evil.example/callback",
            null, null, null, EMAIL, null, EMAIL_CODE, null, PASSWORD)))
            .isSameAs(error);
        verify(accountAuth, never()).register(any());
    }
}
