package com.aieducenter.aieducenteridentity.account.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.aieducenter.aieducenteridentity.account.application.dto.response.SubjectStatus;
import com.aieducenter.aieducenteridentity.account.application.dto.response.SubjectView;
import com.aieducenter.aieducenteridentity.account.domain.aggregate.Account;
import com.aieducenter.aieducenteridentity.account.domain.aggregate.Profile;
import com.aieducenter.aieducenteridentity.account.domain.enums.AccountStatus;
import com.aieducenter.aieducenteridentity.account.domain.error.AccountError;
import com.aieducenter.aieducenteridentity.account.domain.repository.AccountRepository;
import com.aieducenter.aieducenteridentity.account.domain.repository.ProfileRepository;
import com.cartisan.core.exception.CartisanException;
import com.cartisan.core.exception.DomainException;

/**
 * {@link AccountSubjectAppService} 单元测试——subject 声明数据读缝（issue #49）。
 *
 * <p>覆盖：subjectClaims 返 {@link SubjectView}（含可用性 status）、<b>不 gate</b>（停用/锁定照返，
 * 调用方自决）、账号不存在抛 USER_NOT_FOUND。可用性投影（DISABLED 优先于 LOCKED）亦在此验证。</p>
 */
class AccountSubjectAppServiceTest {

    private static final Long USER_ID = 901L;

    private final AccountRepository accountRepository = mock(AccountRepository.class);
    private final ProfileRepository profileRepository = mock(ProfileRepository.class);

    private final AccountSubjectAppService service = new AccountSubjectAppService(accountRepository, profileRepository);

    @Test
    void given_account_with_profile_when_subject_claims_then_returns_full_subject_view() {
        Account account = restore(USER_ID, "alice@aieducenter.com", "13900100001", "hash", AccountStatus.ACTIVE, false);
        when(accountRepository.findById(USER_ID)).thenReturn(Optional.of(account));
        when(profileRepository.findById(USER_ID)).thenReturn(
            Optional.of(Profile.create(USER_ID, "Alice", "https://avatar/a.png")));

        SubjectView result = service.subjectClaims(USER_ID);

        assertThat(result.userId()).isEqualTo(USER_ID);
        assertThat(result.email()).isEqualTo("alice@aieducenter.com");
        assertThat(result.phone()).isEqualTo("13900100001");
        assertThat(result.nickname()).isEqualTo("Alice");
        assertThat(result.avatar()).isEqualTo("https://avatar/a.png");
        assertThat(result.status()).isEqualTo(SubjectStatus.USABLE);
    }

    @Test
    void given_account_without_profile_when_subject_claims_then_nickname_avatar_null() {
        Account account = restore(USER_ID, "alice@aieducenter.com", null, "hash", AccountStatus.ACTIVE, false);
        when(accountRepository.findById(USER_ID)).thenReturn(Optional.of(account));
        when(profileRepository.findById(USER_ID)).thenReturn(Optional.empty());

        SubjectView result = service.subjectClaims(USER_ID);

        assertThat(result.nickname()).isNull();
        assertThat(result.avatar()).isNull();
    }

    @Test
    void given_disabled_account_when_subject_claims_then_returns_disabled_status_without_gate() {
        // 不 gate——停用账号照返（status=DISABLED），由调用方（token 路径）自决抛；userinfo 路径忽略
        Account account = restore(USER_ID, "alice@aieducenter.com", null, "hash", AccountStatus.DISABLED, false);
        when(accountRepository.findById(USER_ID)).thenReturn(Optional.of(account));
        when(profileRepository.findById(USER_ID)).thenReturn(Optional.empty());

        SubjectView result = service.subjectClaims(USER_ID);

        assertThat(result.status()).isEqualTo(SubjectStatus.DISABLED);
    }

    @Test
    void given_locked_account_when_subject_claims_then_returns_locked_status_without_gate() {
        Account account = restore(USER_ID, "alice@aieducenter.com", null, "hash", AccountStatus.ACTIVE, true);
        when(accountRepository.findById(USER_ID)).thenReturn(Optional.of(account));
        when(profileRepository.findById(USER_ID)).thenReturn(Optional.empty());

        SubjectView result = service.subjectClaims(USER_ID);

        assertThat(result.status()).isEqualTo(SubjectStatus.LOCKED);
    }

    @Test
    void given_disabled_and_locked_account_when_subject_claims_then_disabled_wins() {
        // 可用性优先级与 ensureLoginable 一致——DISABLED 优先于 LOCKED
        Account account = restore(USER_ID, "alice@aieducenter.com", null, "hash", AccountStatus.DISABLED, true);
        when(accountRepository.findById(USER_ID)).thenReturn(Optional.of(account));
        when(profileRepository.findById(USER_ID)).thenReturn(Optional.empty());

        SubjectView result = service.subjectClaims(USER_ID);

        assertThat(result.status()).isEqualTo(SubjectStatus.DISABLED);
    }

    @Test
    void given_unknown_user_when_subject_claims_then_user_not_found() {
        when(accountRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.subjectClaims(USER_ID))
            .isInstanceOf(DomainException.class)
            .extracting(ex -> ((CartisanException) ex).getCodeMessage())
            .isEqualTo(AccountError.USER_NOT_FOUND);
    }

    private static Account restore(Long id, String email, String phone, String passwordHash,
            AccountStatus status, boolean locked) {
        return Account.restore(id, email, phone, passwordHash, status, locked, null);
    }
}
