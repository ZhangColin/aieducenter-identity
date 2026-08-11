package com.aieducenter.aieducenteridentity.account.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.aieducenter.aieducenteridentity.account.application.dto.command.RegisterAccountCommand;
import com.aieducenter.aieducenteridentity.account.application.dto.response.SubjectStatus;
import com.aieducenter.aieducenteridentity.account.application.dto.response.SubjectView;
import com.aieducenter.aieducenteridentity.account.domain.aggregate.Account;
import com.aieducenter.aieducenteridentity.account.domain.enums.AccountStatus;
import com.aieducenter.aieducenteridentity.account.domain.error.AccountError;
import com.aieducenter.aieducenteridentity.account.domain.repository.AccountRepository;
import com.aieducenter.aieducenteridentity.account.domain.repository.ProfileRepository;
import com.aieducenter.aieducenteridentity.account.domain.service.AccountPasswordEncoderService;
import com.aieducenter.aieducenteridentity.account.infrastructure.verification.VerificationCodePort;
import com.cartisan.core.exception.ApplicationException;
import com.cartisan.core.exception.CartisanException;
import com.cartisan.core.exception.DomainException;

/**
 * {@link AccountAuthAppService} 单元测试——account 认证 / 建号应用层缝（issue #49）。
 *
 * <p>覆盖：authenticate（防枚举 / {@code ensureLoginable} / {@code recordLogin} / 返 {@link SubjectView}）、
 * authenticateByIdentifier（无密码路径 / 账号不存在）、register（唯一性 / 密码可选）。</p>
 */
class AccountAuthAppServiceTest {

    private static final String EMAIL = "alice@aieducenter.com";
    private static final String PHONE = "13900100001";
    private static final String PASSWORD = "Password123";
    private static final String HASH = "$2a$10$encodedhashvalue";

    private final AccountRepository accountRepository = mock(AccountRepository.class);
    private final AccountPasswordEncoderService passwordEncoderService = mock(AccountPasswordEncoderService.class);
    private final ProfileRepository profileRepository = mock(ProfileRepository.class);
    private final VerificationCodePort verificationCodePort = mock(VerificationCodePort.class);

    private final AccountAuthAppService service = new AccountAuthAppService(
        accountRepository, passwordEncoderService, profileRepository, verificationCodePort);

    // ========== authenticate ==========

    @Test
    void given_valid_phone_credentials_when_authenticate_then_records_login_returns_subject_view() {
        Account account = restore(900L, null, PHONE, HASH, AccountStatus.ACTIVE, false);
        when(accountRepository.findByEmail(PHONE)).thenReturn(Optional.empty());
        when(accountRepository.findByPhone(PHONE)).thenReturn(Optional.of(account));
        when(passwordEncoderService.verifyPassword(PASSWORD, HASH)).thenReturn(true);
        when(profileRepository.findById(900L)).thenReturn(Optional.empty());

        SubjectView result = service.authenticate(PHONE, PASSWORD);

        assertThat(result.userId()).isEqualTo(900L);
        assertThat(result.phone()).isEqualTo(PHONE);
        assertThat(result.status()).isEqualTo(SubjectStatus.USABLE);
        assertThat(result.nickname()).isNull();
        verify(accountRepository).save(account);
        // recordLogin 已落——lastLoginAt 非空
        assertThat(account.getLastLoginAt()).isNotNull();
    }

    @Test
    void given_valid_email_credentials_when_authenticate_then_returns_subject_view_with_profile() {
        Account account = restore(901L, EMAIL, null, HASH, AccountStatus.ACTIVE, false);
        when(accountRepository.findByEmail(EMAIL)).thenReturn(Optional.of(account));
        when(passwordEncoderService.verifyPassword(PASSWORD, HASH)).thenReturn(true);
        when(profileRepository.findById(901L)).thenReturn(
            Optional.of(profileOf(901L, "Alice", "https://avatar/a.png")));

        SubjectView result = service.authenticate(EMAIL, PASSWORD);

        assertThat(result.userId()).isEqualTo(901L);
        assertThat(result.email()).isEqualTo(EMAIL);
        assertThat(result.nickname()).isEqualTo("Alice");
        assertThat(result.avatar()).isEqualTo("https://avatar/a.png");
        assertThat(result.status()).isEqualTo(SubjectStatus.USABLE);
    }

    @Test
    void given_unknown_account_when_authenticate_then_login_password_incorrect_and_no_record() {
        when(accountRepository.findByEmail(PHONE)).thenReturn(Optional.empty());
        when(accountRepository.findByPhone(PHONE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.authenticate(PHONE, PASSWORD))
            .isInstanceOf(DomainException.class)
            .extracting(ex -> ((CartisanException) ex).getCodeMessage())
            .isEqualTo(AccountError.LOGIN_PASSWORD_INCORRECT);
        verify(accountRepository, never()).save(any());
    }

    @Test
    void given_wrong_password_when_authenticate_then_same_error_as_unknown_account() {
        // 防枚举：密码错与账号不存在抛同一 code+message+status
        Account account = restore(900L, null, PHONE, HASH, AccountStatus.ACTIVE, false);
        when(accountRepository.findByEmail(PHONE)).thenReturn(Optional.empty());
        when(accountRepository.findByPhone(PHONE)).thenReturn(Optional.of(account));
        when(passwordEncoderService.verifyPassword(PASSWORD, HASH)).thenReturn(false);

        assertThatThrownBy(() -> service.authenticate(PHONE, PASSWORD))
            .isInstanceOf(DomainException.class)
            .extracting(ex -> ((CartisanException) ex).getCodeMessage())
            .isEqualTo(AccountError.LOGIN_PASSWORD_INCORRECT);
        verify(accountRepository, never()).save(any());
    }

    @Test
    void given_passwordless_account_when_authenticate_by_password_then_login_password_incorrect() {
        // 无密码账号（passwordHash=null）——verifyPassword 恒 false → 与密码错同一响应（防枚举保持）
        Account account = restore(900L, null, PHONE, null, AccountStatus.ACTIVE, false);
        when(accountRepository.findByEmail(PHONE)).thenReturn(Optional.empty());
        when(accountRepository.findByPhone(PHONE)).thenReturn(Optional.of(account));
        when(passwordEncoderService.verifyPassword(PASSWORD, null)).thenReturn(false);

        assertThatThrownBy(() -> service.authenticate(PHONE, PASSWORD))
            .isInstanceOf(DomainException.class)
            .extracting(ex -> ((CartisanException) ex).getCodeMessage())
            .isEqualTo(AccountError.LOGIN_PASSWORD_INCORRECT);
    }

    @Test
    void given_disabled_account_after_password_ok_when_authenticate_then_account_disabled() {
        // 身份已证明（密码通过）——告知停用不构成枚举
        Account account = restore(900L, null, PHONE, HASH, AccountStatus.DISABLED, false);
        when(accountRepository.findByEmail(PHONE)).thenReturn(Optional.empty());
        when(accountRepository.findByPhone(PHONE)).thenReturn(Optional.of(account));
        when(passwordEncoderService.verifyPassword(PASSWORD, HASH)).thenReturn(true);

        assertThatThrownBy(() -> service.authenticate(PHONE, PASSWORD))
            .isInstanceOf(DomainException.class)
            .extracting(ex -> ((CartisanException) ex).getCodeMessage())
            .isEqualTo(AccountError.ACCOUNT_DISABLED);
        verify(accountRepository, never()).save(any());
    }

    @Test
    void given_locked_account_after_password_ok_when_authenticate_then_account_locked() {
        Account account = restore(900L, null, PHONE, HASH, AccountStatus.ACTIVE, true);
        when(accountRepository.findByEmail(PHONE)).thenReturn(Optional.empty());
        when(accountRepository.findByPhone(PHONE)).thenReturn(Optional.of(account));
        when(passwordEncoderService.verifyPassword(PASSWORD, HASH)).thenReturn(true);

        assertThatThrownBy(() -> service.authenticate(PHONE, PASSWORD))
            .isInstanceOf(DomainException.class)
            .extracting(ex -> ((CartisanException) ex).getCodeMessage())
            .isEqualTo(AccountError.ACCOUNT_LOCKED);
    }

    // ========== authenticateByIdentifier ==========

    @Test
    void given_verified_email_when_authenticate_by_identifier_then_records_login_returns_subject_view() {
        Account account = restore(901L, EMAIL, null, null, AccountStatus.ACTIVE, false);
        when(accountRepository.findByEmail(EMAIL)).thenReturn(Optional.of(account));
        when(profileRepository.findById(901L)).thenReturn(Optional.empty());

        SubjectView result = service.authenticateByIdentifier(EMAIL);

        assertThat(result.userId()).isEqualTo(901L);
        assertThat(result.email()).isEqualTo(EMAIL);
        assertThat(result.status()).isEqualTo(SubjectStatus.USABLE);
        verify(accountRepository).save(account);
        assertThat(account.getLastLoginAt()).isNotNull();
        // 无密码路径不碰密码校验
        verify(passwordEncoderService, never()).verifyPassword(any(), any());
    }

    @Test
    void given_verified_phone_with_whitespace_when_authenticate_by_identifier_then_trims_and_locates() {
        Account account = restore(900L, null, PHONE, null, AccountStatus.ACTIVE, false);
        when(accountRepository.findByPhone(PHONE)).thenReturn(Optional.of(account));
        when(profileRepository.findById(900L)).thenReturn(Optional.empty());

        SubjectView result = service.authenticateByIdentifier("  " + PHONE + "  ");

        assertThat(result.phone()).isEqualTo(PHONE);
        verify(accountRepository).findByPhone(PHONE);
    }

    @Test
    void given_identifier_not_found_when_authenticate_by_identifier_then_account_not_found() {
        // 验码已通过、账号不存在——抛 ACCOUNT_NOT_FOUND（ApplicationException），由调用方翻译为防枚举响应
        when(accountRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.authenticateByIdentifier(EMAIL))
            .isInstanceOf(ApplicationException.class)
            .extracting(ex -> ((CartisanException) ex).getCodeMessage())
            .isEqualTo(AccountError.ACCOUNT_NOT_FOUND);
        verify(accountRepository, never()).save(any());
    }

    @Test
    void given_disabled_account_when_authenticate_by_identifier_then_account_disabled() {
        Account account = restore(901L, EMAIL, null, null, AccountStatus.DISABLED, false);
        when(accountRepository.findByEmail(EMAIL)).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> service.authenticateByIdentifier(EMAIL))
            .isInstanceOf(DomainException.class)
            .extracting(ex -> ((CartisanException) ex).getCodeMessage())
            .isEqualTo(AccountError.ACCOUNT_DISABLED);
    }

    // ========== register ==========

    @Test
    void given_new_email_with_password_when_register_then_creates_account_and_records_login() {
        when(accountRepository.existsByEmail(EMAIL)).thenReturn(false);
        when(passwordEncoderService.encodePassword(PASSWORD)).thenReturn(HASH);

        SubjectView result = service.register(new RegisterAccountCommand(EMAIL, null, PASSWORD));

        ArgumentCaptor<Account> saved = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).save(saved.capture());
        Account account = saved.getValue();
        assertThat(account.getEmail()).isEqualTo(EMAIL);
        assertThat(account.getPhone()).isNull();
        assertThat(account.getPasswordHash()).isEqualTo(HASH);
        assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(account.getLastLoginAt()).isNotNull(); // 注册即登录

        // 返回的 SubjectView 投影自新号（尚无 Profile → nickname/avatar null，status USABLE）
        assertThat(result.email()).isEqualTo(EMAIL);
        assertThat(result.nickname()).isNull();
        assertThat(result.avatar()).isNull();
        assertThat(result.status()).isEqualTo(SubjectStatus.USABLE);
    }

    @Test
    void given_new_phone_without_password_when_register_then_creates_passwordless_account() {
        when(accountRepository.existsByPhone(PHONE)).thenReturn(false);

        SubjectView result = service.register(new RegisterAccountCommand(null, PHONE, null));

        ArgumentCaptor<Account> saved = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).save(saved.capture());
        assertThat(saved.getValue().getPhone()).isEqualTo(PHONE);
        assertThat(saved.getValue().getEmail()).isNull();
        assertThat(saved.getValue().getPasswordHash()).isNull(); // 密码可选
        verify(passwordEncoderService, never()).encodePassword(any());
        assertThat(result.phone()).isEqualTo(PHONE);
        assertThat(result.status()).isEqualTo(SubjectStatus.USABLE);
    }

    @Test
    void given_both_contacts_when_register_then_stores_both() {
        when(accountRepository.existsByEmail(EMAIL)).thenReturn(false);
        when(accountRepository.existsByPhone(PHONE)).thenReturn(false);
        when(passwordEncoderService.encodePassword(PASSWORD)).thenReturn(HASH);

        service.register(new RegisterAccountCommand(EMAIL, PHONE, PASSWORD));

        ArgumentCaptor<Account> saved = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).save(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo(EMAIL);
        assertThat(saved.getValue().getPhone()).isEqualTo(PHONE);
    }

    @Test
    void given_blank_password_when_register_then_treated_as_passwordless() {
        when(accountRepository.existsByEmail(EMAIL)).thenReturn(false);

        service.register(new RegisterAccountCommand(EMAIL, null, "   "));

        ArgumentCaptor<Account> saved = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).save(saved.capture());
        assertThat(saved.getValue().getPasswordHash()).isNull();
        verify(passwordEncoderService, never()).encodePassword(any());
    }

    @Test
    void given_existing_email_when_register_then_email_already_exists_and_no_save() {
        when(accountRepository.existsByEmail(EMAIL)).thenReturn(true);

        assertThatThrownBy(() -> service.register(new RegisterAccountCommand(EMAIL, null, PASSWORD)))
            .isInstanceOf(DomainException.class)
            .extracting(ex -> ((CartisanException) ex).getCodeMessage())
            .isEqualTo(AccountError.EMAIL_ALREADY_EXISTS);
        verify(accountRepository, never()).save(any());
        verify(passwordEncoderService, never()).encodePassword(any());
    }

    @Test
    void given_existing_phone_when_register_then_phone_already_exists_and_no_save() {
        when(accountRepository.existsByPhone(PHONE)).thenReturn(true);

        assertThatThrownBy(() -> service.register(new RegisterAccountCommand(null, PHONE, PASSWORD)))
            .isInstanceOf(DomainException.class)
            .extracting(ex -> ((CartisanException) ex).getCodeMessage())
            .isEqualTo(AccountError.PHONE_ALREADY_EXISTS);
        verify(accountRepository, never()).save(any());
    }

    @Test
    void given_neither_contact_when_register_then_contact_required() {
        // 聚合不变量兜底——Account.register 抛 CONTACT_REQUIRED
        assertThatThrownBy(() -> service.register(new RegisterAccountCommand("  ", null, PASSWORD)))
            .isInstanceOf(DomainException.class)
            .extracting(ex -> ((CartisanException) ex).getCodeMessage())
            .isEqualTo(AccountError.CONTACT_REQUIRED);
        verify(accountRepository, never()).save(any());
    }

    // ========== helpers ==========

    private static Account restore(Long id, String email, String phone, String passwordHash,
            AccountStatus status, boolean locked) {
        return Account.restore(id, email, phone, passwordHash, status, locked, null);
    }

    private static com.aieducenter.aieducenteridentity.account.domain.aggregate.Profile profileOf(
            Long userId, String nickname, String avatar) {
        return com.aieducenter.aieducenteridentity.account.domain.aggregate.Profile.create(userId, nickname, avatar);
    }
}
