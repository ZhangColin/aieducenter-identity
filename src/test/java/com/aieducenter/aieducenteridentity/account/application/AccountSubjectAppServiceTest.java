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

    // ========== findSubject（按联络方式查，#60）==========

    @Test
    void given_email_hit_when_find_subject_then_returns_subject_view() {
        Account account = restore(USER_ID, "find@aieducenter.com", null, "hash", AccountStatus.ACTIVE, false);
        when(accountRepository.findByEmail("find@aieducenter.com")).thenReturn(Optional.of(account));
        when(profileRepository.findById(USER_ID)).thenReturn(Optional.empty());

        SubjectView result = service.findSubject("find@aieducenter.com", null);

        assertThat(result.userId()).isEqualTo(USER_ID);
        assertThat(result.email()).isEqualTo("find@aieducenter.com");
    }

    @Test
    void given_phone_hit_when_find_subject_then_returns_subject_view() {
        Account account = restore(USER_ID, null, "13900100999", "hash", AccountStatus.ACTIVE, false);
        when(accountRepository.findByPhone("13900100999")).thenReturn(Optional.of(account));
        when(profileRepository.findById(USER_ID)).thenReturn(Optional.empty());

        SubjectView result = service.findSubject(null, "13900100999");

        assertThat(result.userId()).isEqualTo(USER_ID);
        assertThat(result.phone()).isEqualTo("13900100999");
    }

    @Test
    void given_both_email_and_phone_when_find_subject_then_email_takes_precedence() {
        // email 优先：两个都给时走 findByEmail（findByPhone 不 stub——若误走 phone，orElseThrow → 失败）
        Account account = restore(USER_ID, "both@aieducenter.com", "13900100888", "hash", AccountStatus.ACTIVE, false);
        when(accountRepository.findByEmail("both@aieducenter.com")).thenReturn(Optional.of(account));
        when(profileRepository.findById(USER_ID)).thenReturn(Optional.empty());

        SubjectView result = service.findSubject("both@aieducenter.com", "13900100888");

        assertThat(result.email()).isEqualTo("both@aieducenter.com");
    }

    @Test
    void given_trim_whitespace_around_email_when_find_subject_then_normalizes_before_lookup() {
        // 归一：去首尾空白后再查（与 authenticateByIdentifier 的 trim 口径一致）
        Account account = restore(USER_ID, "trim@aieducenter.com", null, "hash", AccountStatus.ACTIVE, false);
        when(accountRepository.findByEmail("trim@aieducenter.com")).thenReturn(Optional.of(account));
        when(profileRepository.findById(USER_ID)).thenReturn(Optional.empty());

        SubjectView result = service.findSubject("  trim@aieducenter.com  ", null);

        assertThat(result.email()).isEqualTo("trim@aieducenter.com");
    }

    @Test
    void given_email_miss_when_find_subject_then_user_not_found() {
        when(accountRepository.findByEmail("miss@aieducenter.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findSubject("miss@aieducenter.com", null))
            .isInstanceOf(DomainException.class)
            .extracting(ex -> ((CartisanException) ex).getCodeMessage())
            .isEqualTo(AccountError.USER_NOT_FOUND);
    }

    @Test
    void given_phone_miss_when_find_subject_then_user_not_found() {
        when(accountRepository.findByPhone("13900000000")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findSubject(null, "13900000000"))
            .isInstanceOf(DomainException.class)
            .extracting(ex -> ((CartisanException) ex).getCodeMessage())
            .isEqualTo(AccountError.USER_NOT_FOUND);
    }

    @Test
    void given_neither_email_nor_phone_when_find_subject_then_contact_required() {
        // 都没给 → CONTACT_REQUIRED（400）
        assertThatThrownBy(() -> service.findSubject(null, null))
            .isInstanceOf(DomainException.class)
            .extracting(ex -> ((CartisanException) ex).getCodeMessage())
            .isEqualTo(AccountError.CONTACT_REQUIRED);
    }

    @Test
    void given_blank_contacts_when_find_subject_then_contact_required() {
        // 空白串归 null（等同未填）→ CONTACT_REQUIRED
        assertThatThrownBy(() -> service.findSubject("  ", ""))
            .isInstanceOf(DomainException.class)
            .extracting(ex -> ((CartisanException) ex).getCodeMessage())
            .isEqualTo(AccountError.CONTACT_REQUIRED);
    }

    private static Account restore(Long id, String email, String phone, String passwordHash,
            AccountStatus status, boolean locked) {
        return Account.restore(id, email, phone, passwordHash, status, locked, null);
    }
}
