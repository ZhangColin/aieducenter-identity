package com.aieducenter.aieducenteridentity.account.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aieducenter.aieducenteridentity.account.application.dto.command.RegisterCommand;
import com.aieducenter.aieducenteridentity.account.application.dto.event.UserRegisteredEvent;
import com.aieducenter.aieducenteridentity.account.application.dto.response.LoginResponse;
import com.aieducenter.aieducenteridentity.account.domain.aggregate.Account;
import com.aieducenter.aieducenteridentity.account.domain.aggregate.Profile;
import com.aieducenter.aieducenteridentity.account.domain.error.AccountError;
import com.aieducenter.aieducenteridentity.account.domain.repository.AccountRepository;
import com.aieducenter.aieducenteridentity.account.domain.repository.ProfileRepository;
import com.aieducenter.aieducenteridentity.account.domain.service.AccountPasswordEncoderService;
import com.aieducenter.aieducenteridentity.account.infrastructure.verification.VerificationCodePort;
import com.cartisan.core.exception.ApplicationException;
import com.cartisan.core.util.Assertions;
import com.cartisan.event.ApplicationEventPublisher;
import com.cartisan.security.authentication.AuthenticationService;

/**
 * 账号注册应用服务。
 *
 * <p>开放注册：至少留 email 或 phone 之一，填了即当场发码验证、验过才建号（防刷号、保证联络方式真实）。
 * 验证通过后建 {@link Account} + {@link Profile}（1:1）、发注册事件、登录返回 token。</p>
 *
 * @since 0.1.0
 */
@Service
public class AccountRegistrationAppService {

    private static final String REGISTER_PURPOSE = "REGISTER";

    private final AccountRepository accountRepository;
    private final ProfileRepository profileRepository;
    private final AccountPasswordEncoderService passwordEncoderService;
    private final VerificationCodePort verificationCodePort;
    private final AuthenticationService authenticationService;
    private final ApplicationEventPublisher eventPublisher;

    public AccountRegistrationAppService(
            AccountRepository accountRepository,
            ProfileRepository profileRepository,
            AccountPasswordEncoderService passwordEncoderService,
            VerificationCodePort verificationCodePort,
            AuthenticationService authenticationService,
            ApplicationEventPublisher eventPublisher) {
        this.accountRepository = accountRepository;
        this.profileRepository = profileRepository;
        this.passwordEncoderService = passwordEncoderService;
        this.verificationCodePort = verificationCodePort;
        this.authenticationService = authenticationService;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 注册并登录。
     *
     * <p>顺序：至少一联络方式 → 当场验证码（先验码再查重，避免被当作账号存在性探测）→ 查重 →
     * 加密密码（可空）→ 建 account + profile → 发事件 → 登录。</p>
     */
    @Transactional
    public LoginResponse register(RegisterCommand command) {
        boolean hasEmail = isNotBlank(command.email());
        boolean hasPhone = isNotBlank(command.phone());
        Assertions.require(hasEmail || hasPhone, AccountError.CONTACT_REQUIRED);

        // 1. 当场验证联络方式（验过才建号）
        if (hasEmail) {
            verificationCodePort.verifyCode(command.email(), command.emailVerificationCode(), REGISTER_PURPOSE);
        }
        if (hasPhone) {
            verificationCodePort.verifyPhoneCode(command.phone(), command.smsVerificationCode(), REGISTER_PURPOSE);
        }

        // 2. 查重（验码之后：攻击者须先证明掌控联络方式才能探测是否已注册）
        if (hasEmail && accountRepository.existsByEmail(command.email())) {
            throw new ApplicationException(AccountError.EMAIL_ALREADY_EXISTS);
        }
        if (hasPhone && accountRepository.existsByPhone(command.phone())) {
            throw new ApplicationException(AccountError.PHONE_ALREADY_EXISTS);
        }

        // 3. 加密密码（可空——纯验证码/社交账号无密码）
        String encodedPassword = isNotBlank(command.password())
            ? passwordEncoderService.encodePassword(command.password())
            : null;

        // 4. 建 account + profile（1:1）
        Account account = Account.register(command.email(), command.phone(), encodedPassword);
        Account saved = accountRepository.save(account);

        String displayName = resolveDisplayName(command.nickname(), command.email(), command.phone());
        Profile profile = Profile.create(saved.getId(), displayName, null);
        profileRepository.save(profile);

        // 5. 发注册事件（下游消费，如未来钱包 grant / 引导补绑）
        eventPublisher.publishApplicationEvent(
            new UserRegisteredEvent(saved.getId(), saved.getEmail(), saved.getPhone(), profile.getNickname()));

        // 6. 登录返回 token
        return login(saved, displayName);
    }

    private LoginResponse login(Account account, String displayName) {
        account.recordLogin();
        accountRepository.save(account);
        return LoginResponse.fromSession(authenticationService.login(account.getId(), displayName));
    }

    private static String resolveDisplayName(String nickname, String email, String phone) {
        if (isNotBlank(nickname)) {
            return nickname;
        }
        if (isNotBlank(email)) {
            return email;
        }
        return phone;
    }

    private static boolean isNotBlank(String value) {
        return value != null && !value.isBlank();
    }
}
