package com.aieducenter.aieducenteridentity.sso.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.aieducenter.aieducenteridentity.account.domain.aggregate.Account;
import com.aieducenter.aieducenteridentity.account.domain.aggregate.Profile;
import com.aieducenter.aieducenteridentity.account.domain.enums.AccountStatus;
import com.aieducenter.aieducenteridentity.account.domain.repository.ProfileRepository;
import com.aieducenter.aieducenteridentity.sso.application.dto.SsoLoginResult;
import com.aieducenter.aieducenteridentity.sso.domain.client.SsoClient;
import com.aieducenter.aieducenteridentity.sso.domain.session.SsoSession;
import com.aieducenter.aieducenteridentity.sso.domain.session.SsoSessionRepository;

class SsoLoginCompletionAppServiceTest {

    private static final String REDIRECT_URI = "https://demo.localhost/auth/callback";

    private final SsoSessionRepository sessionRepository = mock(SsoSessionRepository.class);
    private final AuthorizationCodeAppService codeService = mock(AuthorizationCodeAppService.class);
    private final ProfileRepository profileRepository = mock(ProfileRepository.class);

    private final SsoLoginCompletionAppService service = new SsoLoginCompletionAppService(
        sessionRepository, codeService, profileRepository);

    private final SsoClient client = new SsoClient("demo-client", "Demo", "hash",
        java.util.Set.of(REDIRECT_URI), java.util.Set.of("openid"), java.util.Set.of("authorization_code"), true);

    private final Account account = Account.restore(900L, "user@aieducenter.com", null, "hash",
        AccountStatus.ACTIVE, false, null);

    @BeforeEach
    void stubSessionAndCode() {
        when(sessionRepository.create(eq(900L), any())).thenReturn(
            new SsoSession("sess-1", 900L, "label", Instant.now(), Instant.now().plusSeconds(60)));
        when(codeService.issueCodeAndRedirect(eq(900L), eq(client), eq(REDIRECT_URI), eq("non"), eq("openid email"), eq("sess-1"), eq("st")))
            .thenReturn(REDIRECT_URI + "?code=ABC&state=st");
    }

    @Test
    void given_profile_with_nickname_when_complete_then_session_display_name_uses_nickname() {
        Profile profile = mock(Profile.class);
        when(profile.getNickname()).thenReturn("阿野");
        when(profileRepository.findById(900L)).thenReturn(Optional.of(profile));

        SsoLoginResult result = service.completeLogin(account, client, REDIRECT_URI, "non", "openid email", "st");

        assertThat(result.sessionId()).isEqualTo("sess-1");
        assertThat(result.redirectUrl()).isEqualTo(REDIRECT_URI + "?code=ABC&state=st");
        verify(sessionRepository).create(900L, "阿野");
    }

    @Test
    void given_no_profile_when_complete_then_display_name_falls_back_to_contact() {
        when(profileRepository.findById(900L)).thenReturn(Optional.empty());

        SsoLoginResult result = service.completeLogin(account, client, REDIRECT_URI, "non", "openid email", "st");

        assertThat(result.sessionId()).isEqualTo("sess-1");
        verify(sessionRepository).create(900L, "user@aieducenter.com");
    }

    @Test
    void given_blank_nickname_when_complete_then_display_name_falls_back_to_contact() {
        Profile profile = mock(Profile.class);
        when(profile.getNickname()).thenReturn("  ");
        when(profileRepository.findById(900L)).thenReturn(Optional.of(profile));

        service.completeLogin(account, client, REDIRECT_URI, "non", "openid email", "st");

        verify(sessionRepository).create(900L, "user@aieducenter.com");
    }

    @Test
    void given_null_optional_params_when_complete_then_passed_through_to_code_issue() {
        when(profileRepository.findById(900L)).thenReturn(Optional.empty());
        when(codeService.issueCodeAndRedirect(eq(900L), eq(client), eq(REDIRECT_URI), isNull(), isNull(), eq("sess-1"), isNull()))
            .thenReturn(REDIRECT_URI + "?code=ABC");

        SsoLoginResult result = service.completeLogin(account, client, REDIRECT_URI, null, null, null);

        assertThat(result.redirectUrl()).isEqualTo(REDIRECT_URI + "?code=ABC");
    }
}
