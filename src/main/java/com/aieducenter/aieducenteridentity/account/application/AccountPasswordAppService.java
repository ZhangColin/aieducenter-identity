package com.aieducenter.aieducenteridentity.account.application;

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

/**
 * 账号密码管理应用服务——重置密码（验证码）+ 修改密码（验旧密码）。
 *
 * @since 0.1.0
 */
@Service
@Transactional
public class AccountPasswordAppService {

    private static final String RESET_PURPOSE = "RESET_PASSWORD";

    private final AccountRepository accountRepository;
    private final AccountPasswordEncoderService passwordEncoderService;
    private final VerificationCodePort verificationCodePort;
    private final SsoSessionRevoker sessionRevoker;

    public AccountPasswordAppService(
            AccountRepository accountRepository,
            AccountPasswordEncoderService passwordEncoderService,
            VerificationCodePort verificationCodePort,
            SsoSessionRevoker sessionRevoker) {
        this.accountRepository = accountRepository;
        this.passwordEncoderService = passwordEncoderService;
        this.verificationCodePort = verificationCodePort;
        this.sessionRevoker = sessionRevoker;
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
        sessionRevoker.revokeQuietly(user.getId());
    }

    /**
     * 修改密码（验证旧密码；需登录态）。SSO 浏览器闭环（{@code /api/sso/change-password}）专用——
     * 从 {@link RequestContext} 取登录态 userId，委托 {@link #changePassword(Long, ChangePasswordCommand)}。
     *
     * @throws DomainException PASSWORD_INCORRECT (旧密码错误) / PASSWORD_SAME_AS_OLD (新旧相同)
     */
    public void changePassword(ChangePasswordCommand command) {
        changePassword(RequestContext.getUserId(), command);
    }

    /**
     * 修改指定用户密码（验证旧密码；改后踢出所有会话）。签名服务
     * （{@code POST /api/account/{userId}/change-password}）专用——签名路径下
     * {@link RequestContext#getUserId()} 为 null（无终端用户登录态，只有调用方 appName），
     * 故 userId 由 controller 从 path 传入，而非读 RequestContext。
     *
     * @param userId  目标用户 ID（签名路径下由 path 提供，非 RequestContext）
     * @param command 旧 / 新明文密码
     * @throws DomainException USER_NOT_FOUND（userId 无对应账号）
     * @throws DomainException PASSWORD_INCORRECT (旧密码错误) / PASSWORD_SAME_AS_OLD (新旧相同)
     */
    public void changePassword(Long userId, ChangePasswordCommand command) {
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
        sessionRevoker.revokeQuietly(account.getId());
    }
}
