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

import com.aieducenter.aieducenteridentity.account.application.AccountAuthAppService;
import com.aieducenter.aieducenteridentity.account.application.dto.response.SubjectStatus;
import com.aieducenter.aieducenteridentity.account.application.dto.response.SubjectView;
import com.aieducenter.aieducenteridentity.account.domain.error.AccountError;
import com.aieducenter.aieducenteridentity.sso.application.dto.LoginByCodeSsoCommand;
import com.aieducenter.aieducenteridentity.sso.application.dto.SsoLoginResult;
import com.aieducenter.aieducenteridentity.sso.domain.client.SsoClient;
import com.aieducenter.aieducenteridentity.sso.domain.client.SsoClientValidationService;
import com.aieducenter.aieducenteridentity.sso.domain.error.OidcException;
import com.aieducenter.aieducenteridentity.sso.domain.error.SsoError;
import com.aieducenter.aieducenteridentity.sso.infrastructure.verification.VerificationCodePort;
import com.aieducenter.aieducenteridentity.verification.domain.error.VerificationCodeError;
import com.cartisan.core.exception.ApplicationException;
import com.cartisan.core.exception.DomainException;

/**
 * sso 验证码登录经 {@link AccountAuthAppService#authenticateByIdentifier} 委托。
 *
 * <p>核心契约：{@code authenticateByIdentifier} 在账号定位不到时抛 {@link ApplicationException}(
 * {@code ACCOUNT_NOT_FOUND})——本服务<b>翻译</b>为与错码同一的 {@link VerificationCodeError#CODE_INVALID}
 * （防枚举）；停用/锁定是 {@link DomainException}，<b>原样透传</b>（身份已证明，告知不构成枚举）。</p>
 */
class SsoLoginCodeAppServiceTest {

    private static final String CLIENT_ID = "demo-client";
    private static final String REDIRECT_URI = "https://demo.localhost/auth/callback";
    private static final String EMAIL = "user@aieducenter.com";
    private static final String PHONE = "13900100001";
    private static final String CODE = "123456";

    private final SsoClientValidationService clientValidation = mock(SsoClientValidationService.class);
    private final AccountAuthAppService accountAuth = mock(AccountAuthAppService.class);
    private final VerificationCodePort verificationCodePort = mock(VerificationCodePort.class);
    private final SsoLoginCompletionAppService loginCompletion = mock(SsoLoginCompletionAppService.class);

    private final SsoLoginCodeAppService service = new SsoLoginCodeAppService(clientValidation, accountAuth,
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

    @Test
    void given_valid_email_code_when_login_then_verify_with_login_purpose_and_delegate_to_completion() {
        SubjectView subject = new SubjectView(900L, EMAIL, null, null, null, SubjectStatus.USABLE);
        when(accountAuth.authenticateByIdentifier(EMAIL)).thenReturn(subject);
        when(loginCompletion.completeLogin(subject, client, REDIRECT_URI, "non", "openid email", "st"))
            .thenReturn(new SsoLoginResult("sess-1", REDIRECT_URI + "?code=ABC&state=st"));

        SsoLoginResult result = service.loginByCode(emailCommand());

        assertThat(result.sessionId()).isEqualTo("sess-1");
        assertThat(result.redirectUrl()).isEqualTo(REDIRECT_URI + "?code=ABC&state=st");
        verify(verificationCodePort).verifyCode(EMAIL, CODE, "LOGIN");
        verify(accountAuth).authenticateByIdentifier(EMAIL);
    }

    @Test
    void given_valid_phone_code_when_login_then_verify_phone_code() {
        SubjectView subject = new SubjectView(901L, null, PHONE, null, null, SubjectStatus.USABLE);
        when(accountAuth.authenticateByIdentifier(PHONE)).thenReturn(subject);
        when(loginCompletion.completeLogin(eq(subject), eq(client), eq(REDIRECT_URI), isNull(), isNull(), isNull()))
            .thenReturn(new SsoLoginResult("sess-2", REDIRECT_URI + "?code=XYZ"));

        SsoLoginResult result = service.loginByCode(
            new LoginByCodeSsoCommand(CLIENT_ID, REDIRECT_URI, null, null, null, PHONE, CODE));

        assertThat(result.sessionId()).isEqualTo("sess-2");
        verify(verificationCodePort).verifyPhoneCode(PHONE, CODE, "LOGIN");
    }

    @Test
    void given_wrong_code_when_login_then_code_invalid_and_no_authenticate_no_session() {
        doThrow(new DomainException(VerificationCodeError.CODE_INVALID))
            .when(verificationCodePort).verifyCode(EMAIL, "000000", "LOGIN");

        assertThatThrownBy(() -> service.loginByCode(
            new LoginByCodeSsoCommand(CLIENT_ID, REDIRECT_URI, null, null, null, EMAIL, "000000")))
            .isInstanceOf(DomainException.class)
            .extracting(ex -> ((DomainException) ex).getCodeMessage())
            .isEqualTo(VerificationCodeError.CODE_INVALID);
        verify(accountAuth, never()).authenticateByIdentifier(any());
        verify(loginCompletion, never()).completeLogin(any(), any(), any(), any(), any(), any());
    }

    /** 防枚举 AC：验码通过但账号不存在（authenticateByIdentifier 抛 ACCOUNT_NOT_FOUND）→ 与错码同一 CODE_INVALID。 */
    @Test
    void given_valid_code_but_unknown_account_when_login_then_translate_to_code_invalid() {
        when(accountAuth.authenticateByIdentifier(EMAIL))
            .thenThrow(new ApplicationException(AccountError.ACCOUNT_NOT_FOUND));

        assertThatThrownBy(() -> service.loginByCode(emailCommand()))
            .isInstanceOf(DomainException.class)
            .extracting(ex -> ((DomainException) ex).getCodeMessage())
            .isEqualTo(VerificationCodeError.CODE_INVALID);
        verify(loginCompletion, never()).completeLogin(any(), any(), any(), any(), any(), any());
    }

    /**
     * 停用 / 锁定是 DomainException——<b>不被翻译</b>，原样透传（与上一用例对照，锁定 ApplicationException 与
     * DomainException 的处理差异：前者翻译、后者透传）。
     */
    @Test
    void given_disabled_account_when_login_then_disabled_propagates_not_translated() {
        when(accountAuth.authenticateByIdentifier(EMAIL))
            .thenThrow(new DomainException(AccountError.ACCOUNT_DISABLED));

        assertThatThrownBy(() -> service.loginByCode(emailCommand()))
            .isInstanceOf(DomainException.class)
            .extracting(ex -> ((DomainException) ex).getCodeMessage())
            .isEqualTo(AccountError.ACCOUNT_DISABLED);
        verify(loginCompletion, never()).completeLogin(any(), any(), any(), any(), any(), any());
    }

    @Test
    void given_unknown_client_when_login_then_unauthorized_client_and_no_authenticate() {
        OidcException error = new OidcException(SsoError.UNAUTHORIZED_CLIENT, "client_id 无效或未注册");
        when(clientValidation.requireActiveClient("ghost")).thenThrow(error);

        assertThatThrownBy(() -> service.loginByCode(
            new LoginByCodeSsoCommand("ghost", REDIRECT_URI, null, null, null, EMAIL, CODE)))
            .isSameAs(error);
        verify(accountAuth, never()).authenticateByIdentifier(any());
    }

    @Test
    void given_redirect_uri_not_whitelisted_when_login_then_invalid_request_and_no_authenticate() {
        OidcException error = new OidcException(SsoError.INVALID_REQUEST, "redirect_uri 未登记");
        doThrow(error).when(clientValidation).requireRedirectUri(eq(client), eq("https://evil.example/callback"));

        assertThatThrownBy(() -> service.loginByCode(
            new LoginByCodeSsoCommand(CLIENT_ID, "https://evil.example/callback", null, null, null, EMAIL, CODE)))
            .isSameAs(error);
        verify(accountAuth, never()).authenticateByIdentifier(any());
    }
}
