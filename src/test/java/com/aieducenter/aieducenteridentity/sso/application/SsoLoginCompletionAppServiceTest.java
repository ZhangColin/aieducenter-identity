package com.aieducenter.aieducenteridentity.sso.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.aieducenter.aieducenteridentity.account.application.dto.response.SubjectStatus;
import com.aieducenter.aieducenteridentity.account.application.dto.response.SubjectView;
import com.aieducenter.aieducenteridentity.sso.application.dto.SsoLoginResult;
import com.aieducenter.aieducenteridentity.sso.domain.client.SsoClient;
import com.aieducenter.aieducenteridentity.sso.domain.session.SsoSession;
import com.aieducenter.aieducenteridentity.sso.domain.session.SsoSessionRepository;

class SsoLoginCompletionAppServiceTest {

    private static final String REDIRECT_URI = "https://demo.localhost/auth/callback";
    private static final Long USER_ID = 900L;

    private final SsoSessionRepository sessionRepository = mock(SsoSessionRepository.class);
    private final AuthorizationCodeAppService codeService = mock(AuthorizationCodeAppService.class);

    private final SsoLoginCompletionAppService service = new SsoLoginCompletionAppService(
        sessionRepository, codeService);

    private final SsoClient client = new SsoClient("demo-client", "Demo", "hash",
        java.util.Set.of(REDIRECT_URI), java.util.Set.of(), java.util.Set.of("openid"), java.util.Set.of("authorization_code"), true);

    @BeforeEach
    void stubSessionAndCode() {
        when(sessionRepository.create(eq(USER_ID), any())).thenReturn(
            new SsoSession("sess-1", USER_ID, "label", Instant.now(), Instant.now().plusSeconds(60)));
        when(codeService.issueCodeAndRedirect(eq(USER_ID), eq(client), eq(REDIRECT_URI), eq("non"), eq("openid email"), eq("sess-1"), eq("st")))
            .thenReturn(REDIRECT_URI + "?code=ABC&state=st");
    }

    /** SubjectView 携带 Profile 昵称——显示名用昵称（accountAuth 投影时装载，sso 不再读 Profile）。 */
    @Test
    void given_subject_with_nickname_when_complete_then_session_display_name_uses_nickname() {
        SubjectView subject = new SubjectView(USER_ID, "user@aieducenter.com", null, "阿野", null, SubjectStatus.USABLE);

        SsoLoginResult result = service.completeLogin(subject, client, REDIRECT_URI, "non", "openid email", "st");

        assertThat(result.sessionId()).isEqualTo("sess-1");
        assertThat(result.redirectUrl()).isEqualTo(REDIRECT_URI + "?code=ABC&state=st");
        verify(sessionRepository).create(USER_ID, "阿野");
    }

    /** 新注册 / 未编辑资料——昵称为 null，显示名落邮箱（SubjectView.displayLabel 口径与 Account 一致）。 */
    @Test
    void given_subject_without_nickname_when_complete_then_display_name_falls_back_to_contact() {
        SubjectView subject = new SubjectView(USER_ID, "user@aieducenter.com", null, null, null, SubjectStatus.USABLE);

        SsoLoginResult result = service.completeLogin(subject, client, REDIRECT_URI, "non", "openid email", "st");

        assertThat(result.sessionId()).isEqualTo("sess-1");
        verify(sessionRepository).create(USER_ID, "user@aieducenter.com");
    }

    @Test
    void given_blank_nickname_when_complete_then_display_name_falls_back_to_contact() {
        SubjectView subject = new SubjectView(USER_ID, "user@aieducenter.com", null, "  ", null, SubjectStatus.USABLE);

        service.completeLogin(subject, client, REDIRECT_URI, "non", "openid email", "st");

        verify(sessionRepository).create(USER_ID, "user@aieducenter.com");
    }

    @Test
    void given_null_optional_params_when_complete_then_passed_through_to_code_issue() {
        SubjectView subject = new SubjectView(USER_ID, "user@aieducenter.com", null, null, null, SubjectStatus.USABLE);
        when(codeService.issueCodeAndRedirect(eq(USER_ID), eq(client), eq(REDIRECT_URI), isNull(), isNull(), eq("sess-1"), isNull()))
            .thenReturn(REDIRECT_URI + "?code=ABC");

        SsoLoginResult result = service.completeLogin(subject, client, REDIRECT_URI, null, null, null);

        assertThat(result.redirectUrl()).isEqualTo(REDIRECT_URI + "?code=ABC");
    }
}
