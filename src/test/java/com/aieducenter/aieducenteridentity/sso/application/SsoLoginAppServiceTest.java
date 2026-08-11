package com.aieducenter.aieducenteridentity.sso.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
import com.aieducenter.aieducenteridentity.sso.application.dto.LoginByPasswordSsoCommand;
import com.aieducenter.aieducenteridentity.sso.application.dto.SsoLoginResult;
import com.aieducenter.aieducenteridentity.sso.domain.client.SsoClient;
import com.aieducenter.aieducenteridentity.sso.domain.client.SsoClientValidationService;
import com.aieducenter.aieducenteridentity.sso.domain.error.OidcException;
import com.aieducenter.aieducenteridentity.sso.domain.error.SsoError;
import com.cartisan.core.exception.DomainException;

/**
 * sso 密码登录经 {@link AccountAuthAppService#authenticate} 委托——防枚举 / 状态机收在 account 应用层，
 * 本服务只验 client/redirect + 拿 SubjectView 交后半段。
 */
class SsoLoginAppServiceTest {

    private static final String CLIENT_ID = "demo-client";
    private static final String REDIRECT_URI = "https://demo.localhost/auth/callback";
    private static final String ACCOUNT = "13900100001";
    private static final String PASSWORD = "Password123";

    private final SsoClientValidationService clientValidation = mock(SsoClientValidationService.class);
    private final AccountAuthAppService accountAuth = mock(AccountAuthAppService.class);
    private final SsoLoginCompletionAppService loginCompletion = mock(SsoLoginCompletionAppService.class);

    private final SsoLoginAppService service = new SsoLoginAppService(clientValidation, accountAuth, loginCompletion);

    private final SsoClient client = new SsoClient(CLIENT_ID, "Demo", "hash",
        java.util.Set.of(REDIRECT_URI), java.util.Set.of(), java.util.Set.of("openid"), java.util.Set.of("authorization_code"), true);

    @BeforeEach
    void stubClient() {
        when(clientValidation.requireActiveClient(CLIENT_ID)).thenReturn(client);
    }

    private LoginByPasswordSsoCommand command() {
        return new LoginByPasswordSsoCommand(CLIENT_ID, REDIRECT_URI, "st", "non@ce", "openid profile", ACCOUNT, PASSWORD);
    }

    private SubjectView subject() {
        return new SubjectView(900L, null, ACCOUNT, null, null, SubjectStatus.USABLE);
    }

    @Test
    void given_valid_credentials_when_login_then_authenticate_and_delegate_to_completion() {
        SubjectView subject = subject();
        when(accountAuth.authenticate(ACCOUNT, PASSWORD)).thenReturn(subject);
        when(loginCompletion.completeLogin(subject, client, REDIRECT_URI, "non@ce", "openid profile", "st"))
            .thenReturn(new SsoLoginResult("sess-1", REDIRECT_URI + "?code=ABC&state=st"));

        SsoLoginResult result = service.loginByPassword(command());

        assertThat(result.sessionId()).isEqualTo("sess-1");
        assertThat(result.redirectUrl()).isEqualTo(REDIRECT_URI + "?code=ABC&state=st");
        verify(accountAuth).authenticate(ACCOUNT, PASSWORD);
        verify(loginCompletion).completeLogin(subject, client, REDIRECT_URI, "non@ce", "openid profile", "st");
    }

    /** 防枚举（账号不存在 / 密码错统一）收在 authenticate——异常透传，不进后半段。 */
    @Test
    void given_authenticate_rejects_when_login_then_propagate_and_no_completion() {
        when(accountAuth.authenticate(ACCOUNT, PASSWORD))
            .thenThrow(new DomainException(AccountError.LOGIN_PASSWORD_INCORRECT));

        assertThatThrownBy(() -> service.loginByPassword(command()))
            .isInstanceOf(DomainException.class)
            .extracting(ex -> ((DomainException) ex).getCodeMessage())
            .isEqualTo(AccountError.LOGIN_PASSWORD_INCORRECT);
        verify(loginCompletion, never()).completeLogin(any(), any(), any(), any(), any(), any());
    }

    /** 停用账号——authenticate 抛 ACCOUNT_DISABLED，透传（身份已证明，告知不构成枚举）。 */
    @Test
    void given_disabled_account_when_login_then_disabled_propagates() {
        when(accountAuth.authenticate(ACCOUNT, PASSWORD))
            .thenThrow(new DomainException(AccountError.ACCOUNT_DISABLED));

        assertThatThrownBy(() -> service.loginByPassword(command()))
            .isInstanceOf(DomainException.class)
            .extracting(ex -> ((DomainException) ex).getCodeMessage())
            .isEqualTo(AccountError.ACCOUNT_DISABLED);
        verify(loginCompletion, never()).completeLogin(any(), any(), any(), any(), any(), any());
    }

    @Test
    void given_unknown_client_when_login_then_unauthorized_client_and_no_authenticate() {
        OidcException error = new OidcException(SsoError.UNAUTHORIZED_CLIENT, "client_id 无效或未注册");
        when(clientValidation.requireActiveClient("ghost")).thenThrow(error);

        assertThatThrownBy(() -> service.loginByPassword(
            new LoginByPasswordSsoCommand("ghost", REDIRECT_URI, null, null, null, ACCOUNT, PASSWORD)))
            .isSameAs(error);
        verify(accountAuth, never()).authenticate(any(), any());
    }

    @Test
    void given_redirect_uri_not_whitelisted_when_login_then_invalid_request_and_no_authenticate() {
        OidcException error = new OidcException(SsoError.INVALID_REQUEST, "redirect_uri 未登记");
        org.mockito.Mockito.doThrow(error).when(clientValidation)
            .requireRedirectUri(eq(client), eq("https://evil.example/callback"));

        assertThatThrownBy(() -> service.loginByPassword(
            new LoginByPasswordSsoCommand(CLIENT_ID, "https://evil.example/callback", null, null, null, ACCOUNT, PASSWORD)))
            .isSameAs(error);
        verify(accountAuth, never()).authenticate(any(), any());
    }
}
