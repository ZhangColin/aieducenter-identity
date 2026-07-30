package com.aieducenter.aieducenteridentity.account.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aieducenter.aieducenteridentity.account.application.dto.command.ChangePasswordCommand;
import com.aieducenter.aieducenteridentity.account.application.dto.command.ResetPasswordCommand;
import com.aieducenter.aieducenteridentity.account.domain.aggregate.Account;
import com.aieducenter.aieducenteridentity.account.domain.error.AccountError;
import com.aieducenter.aieducenteridentity.account.domain.repository.AccountRepository;
import com.aieducenter.aieducenteridentity.account.domain.service.AccountPasswordEncoderService;
import com.aieducenter.aieducenteridentity.account.infrastructure.verification.VerificationCodePort;
import com.cartisan.core.context.RequestContext;
import com.cartisan.core.exception.ApplicationException;
import com.cartisan.core.exception.DomainException;
import com.cartisan.security.authentication.AuthenticationService;

/**
 * 账号密码管理应用服务——重置密码（验证码）+ 修改密码（验旧密码）。
 *
 * @since 0.1.0
 */
@Service
@Transactional
public class AccountPasswordAppService {

    private static final Logger log = LoggerFactory.getLogger(AccountPasswordAppService.class);
    private static final String RESET_PURPOSE = "RESET_PASSWORD";

    private final AccountRepository accountRepository;
    private final AccountPasswordEncoderService passwordEncoderService;
    private final VerificationCodePort verificationCodePort;
    private final AuthenticationService authenticationService;

    public AccountPasswordAppService(
            AccountRepository accountRepository,
            AccountPasswordEncoderService passwordEncoderService,
            VerificationCodePort verificationCodePort,
            AuthenticationService authenticationService) {
        this.accountRepository = accountRepository;
        this.passwordEncoderService = passwordEncoderService;
        this.verificationCodePort = verificationCodePort;
        this.authenticationService = authenticationService;
    }

    /**
     * 重置密码（经手机/邮箱验证码，无需旧密码）。重置后踢出所有已登录会话。
     *
     * @throws DomainException 验证码错误/过期/已用
     * @throws ApplicationException 账号不存在（验证码已通过、调用方掌控联络方式时才到达）
     */
    public void resetPassword(ResetPasswordCommand command) {
        String account = command.account();
        boolean isEmail = account.contains("@");

        // 1. 校验验证码（未注册联络方式无有效码 → CODE_INVALID，天然不暴露存在性）
        if (isEmail) {
            verificationCodePort.verifyCode(account, command.verificationCode(), RESET_PURPOSE);
        } else {
            verificationCodePort.verifyPhoneCode(account, command.verificationCode(), RESET_PURPOSE);
        }

        // 2. 查找账号
        Account user = isEmail
            ? accountRepository.findByEmail(account).orElseThrow(() -> new ApplicationException(AccountError.ACCOUNT_NOT_FOUND))
            : accountRepository.findByPhone(account).orElseThrow(() -> new ApplicationException(AccountError.ACCOUNT_NOT_FOUND));

        // 3. 重置 + 踢出会话
        user.resetPassword(passwordEncoderService.encodePassword(command.newPassword()));
        accountRepository.save(user);
        kickoutQuietly(user.getId());
    }

    /**
     * 修改密码（验证旧密码；需登录态）。
     *
     * @throws DomainException PASSWORD_INCORRECT (旧密码错误) / PASSWORD_SAME_AS_OLD (新旧相同)
     */
    public void changePassword(ChangePasswordCommand command) {
        Long userId = RequestContext.getUserId();
        Account account = accountRepository.findById(userId)
            .orElseThrow(() -> new DomainException(AccountError.USER_NOT_FOUND));

        if (!passwordEncoderService.verifyPassword(command.oldPassword(), account.getPasswordHash())) {
            throw new DomainException(AccountError.PASSWORD_INCORRECT);
        }
        if (command.oldPassword().equals(command.newPassword())) {
            throw new DomainException(AccountError.PASSWORD_SAME_AS_OLD);
        }

        account.changePassword(passwordEncoderService.encodePassword(command.newPassword()));
        accountRepository.save(account);
        kickoutQuietly(account.getId());
    }

    private void kickoutQuietly(Long userId) {
        try {
            authenticationService.kickout(userId);
        } catch (Exception e) {
            log.warn("kickout failed userId={}", userId, e);
        }
    }
}
