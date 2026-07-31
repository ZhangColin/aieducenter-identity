package com.aieducenter.aieducenteridentity.account.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aieducenter.aieducenteridentity.account.application.dto.command.LoginByPasswordCommand;
import com.aieducenter.aieducenteridentity.account.application.dto.command.LoginBySmsCommand;
import com.aieducenter.aieducenteridentity.account.application.dto.response.LoginResponse;
import com.aieducenter.aieducenteridentity.account.domain.aggregate.Account;
import com.aieducenter.aieducenteridentity.account.domain.aggregate.Profile;
import com.aieducenter.aieducenteridentity.account.domain.error.AccountError;
import com.aieducenter.aieducenteridentity.account.domain.repository.AccountRepository;
import com.aieducenter.aieducenteridentity.account.domain.repository.ProfileRepository;
import com.aieducenter.aieducenteridentity.account.domain.service.AccountPasswordEncoderService;
import com.aieducenter.aieducenteridentity.account.infrastructure.verification.CaptchaPort;
import com.aieducenter.aieducenteridentity.account.infrastructure.verification.VerificationCodePort;
import com.cartisan.core.exception.ApplicationException;
import com.cartisan.core.exception.DomainException;
import com.cartisan.security.authentication.AuthenticationService;

/**
 * 账号登录应用服务。
 *
 * <p>密码登录 + 短信验证码登录 + 登出。</p>
 *
 * <h3>防用户枚举</h3>
 * <p>密码登录时，账号不存在与密码错误统一抛 {@link AccountError#LOGIN_PASSWORD_INCORRECT}（同一 code+message），
 * 不暴露账号存在性。停用/锁定检查在密码通过后才执行——身份已证明，告知原因不构成枚举。</p>
 *
 * @since 0.1.0
 */
@Service
public class AccountLoginAppService {

    private static final String LOGIN_PURPOSE = "LOGIN";

    private final AccountRepository accountRepository;
    private final ProfileRepository profileRepository;
    private final AccountPasswordEncoderService passwordEncoderService;
    private final CaptchaPort captchaPort;
    private final VerificationCodePort verificationCodePort;
    private final AccountTokenAppService accountTokenAppService;
    private final AuthenticationService authenticationService;

    public AccountLoginAppService(
            AccountRepository accountRepository,
            ProfileRepository profileRepository,
            AccountPasswordEncoderService passwordEncoderService,
            CaptchaPort captchaPort,
            VerificationCodePort verificationCodePort,
            AccountTokenAppService accountTokenAppService,
            AuthenticationService authenticationService) {
        this.accountRepository = accountRepository;
        this.profileRepository = profileRepository;
        this.passwordEncoderService = passwordEncoderService;
        this.captchaPort = captchaPort;
        this.verificationCodePort = verificationCodePort;
        this.accountTokenAppService = accountTokenAppService;
        this.authenticationService = authenticationService;
    }

    /**
     * 密码登录（account = 邮箱或手机号）。
     *
     * @throws DomainException LOGIN_PASSWORD_INCORRECT (401, 账号不存在/密码错误统一)
     * @throws DomainException ACCOUNT_DISABLED / ACCOUNT_LOCKED (401, 身份验证通过后)
     */
    @Transactional
    public LoginResponse loginByPassword(LoginByPasswordCommand command) {
        // 1. 图形验证码
        captchaPort.verifyCaptcha(command.captchaId(), command.captchaCode());

        // 2. 查找账号（email 或 phone）
        Account account = accountRepository.findByEmail(command.account())
            .or(() -> accountRepository.findByPhone(command.account()))
            .orElse(null);

        // 3. 防枚举：账号不存在与密码错误统一返回 LOGIN_PASSWORD_INCORRECT
        if (account == null
            || !passwordEncoderService.verifyPassword(command.password(), account.getPasswordHash())) {
            throw new DomainException(AccountError.LOGIN_PASSWORD_INCORRECT);
        }

        // 4. 身份已证明——检查停用/锁定（具体原因）
        account.ensureLoginable();

        // 5. 记录登录 + 签发 token（access/id JWT，issue #11）。access JWT 兼作 Sa-Token 会话 token，
        // bug#1（userName 写入 SaSession 供 RequestContext 读取）在 AccountTokenAppService→IdpSessionRegistrar 保留。
        account.recordLogin();
        accountRepository.save(account);
        Profile profile = profileRepository.findById(account.getId()).orElse(null);
        return accountTokenAppService.issue(account, profile);
    }

    /**
     * 短信验证码登录。
     *
     * <p>图形验证码已在发送短信验证码时校验。短信码先校验（未注册手机无有效码 → CODE_INVALID），
     * 再查账号、检查停用/锁定。</p>
     *
     * @throws DomainException 验证码错误/过期/已用，或 ACCOUNT_NOT_FOUND / ACCOUNT_DISABLED / ACCOUNT_LOCKED
     */
    @Transactional
    public LoginResponse loginBySms(LoginBySmsCommand command) {
        verificationCodePort.verifyPhoneCode(command.phone(), command.code(), LOGIN_PURPOSE);

        Account account = accountRepository.findByPhone(command.phone())
            .orElseThrow(() -> new ApplicationException(AccountError.ACCOUNT_NOT_FOUND));

        account.ensureLoginable();

        account.recordLogin();
        accountRepository.save(account);
        Profile profile = profileRepository.findById(account.getId()).orElse(null);
        return accountTokenAppService.issue(account, profile);
    }

    /**
     * 登出——清除当前服务端会话。
     */
    public void logout() {
        authenticationService.logout();
    }
}
