package com.aieducenter.aieducenteridentity.sso.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aieducenter.aieducenteridentity.account.domain.aggregate.Account;
import com.aieducenter.aieducenteridentity.account.domain.repository.AccountRepository;
import com.aieducenter.aieducenteridentity.sso.application.dto.LoginByCodeSsoCommand;
import com.aieducenter.aieducenteridentity.sso.application.dto.SsoLoginResult;
import com.aieducenter.aieducenteridentity.sso.domain.client.SsoClient;
import com.aieducenter.aieducenteridentity.sso.domain.client.SsoClientValidationService;
import com.aieducenter.aieducenteridentity.sso.domain.error.OidcException;
import com.aieducenter.aieducenteridentity.sso.infrastructure.verification.VerificationCodePort;
import com.aieducenter.aieducenteridentity.verification.domain.enums.VerificationPurpose;
import com.aieducenter.aieducenteridentity.verification.domain.error.VerificationCodeError;
import com.cartisan.core.exception.DomainException;

/**
 * /api/auth/login-code 验证码登录（CONTEXT 登录契约 / issue #22）。
 *
 * <p>校验 client/redirect_uri → 验码（purpose=LOGIN，经 verification 上下文）→ 定位账号 →
 * {@link SsoLoginCompletionAppService 统一后半段}：建 SSO 会话 → 发 code。
 * 不设密码的账号（注册时密码可选）由此入口登录。</p>
 *
 * <h3>防用户枚举</h3>
 * <p>「账号不存在」与「验证码错误」同一响应：错码由 verification 抛 {@link VerificationCodeError#CODE_INVALID}；
 * 验码通过但账号不存在（LOGIN 码可对任意联络方式下发）补抛同一 {@code CODE_INVALID}——同一 code+message+status，
 * 不暴露账号存在性。停用/锁定在验码通过（身份已证明）后才告知，不构成枚举。</p>
 *
 * @since 0.1.0
 */
@Service
public class SsoLoginCodeAppService {

    /** 验证码用途：登录（与注册的 REGISTER 分键，互不串用）。 */
    private static final String LOGIN_PURPOSE = VerificationPurpose.LOGIN.name();

    private final SsoClientValidationService clientValidation;
    private final AccountRepository accountRepository;
    private final VerificationCodePort verificationCodePort;
    private final SsoLoginCompletionAppService loginCompletion;

    public SsoLoginCodeAppService(SsoClientValidationService clientValidation, AccountRepository accountRepository,
            VerificationCodePort verificationCodePort, SsoLoginCompletionAppService loginCompletion) {
        this.clientValidation = clientValidation;
        this.accountRepository = accountRepository;
        this.verificationCodePort = verificationCodePort;
        this.loginCompletion = loginCompletion;
    }

    /**
     * 验证码登录 → 建 SSO 会话 + 发 code。
     *
     * @throws OidcException   client/redirect_uri 无效（400，不重定向）
     * @throws DomainException CODE_INVALID（400，验证码错误/账号不存在统一）/ CODE_EXPIRED / CODE_ALREADY_USED /
     *                         ACCOUNT_DISABLED / ACCOUNT_LOCKED
     */
    @Transactional
    public SsoLoginResult loginByCode(LoginByCodeSsoCommand command) {
        SsoClient client = clientValidation.requireActiveClient(command.clientId());
        clientValidation.requireRedirectUri(client, command.redirectUri());

        String account = command.account().trim();
        boolean isEmail = account.contains("@");

        // 先验码（错码即 CODE_INVALID）——未注册联络方式通常无有效码，天然不暴露存在性
        if (isEmail) {
            verificationCodePort.verifyCode(account, command.code(), LOGIN_PURPOSE);
        } else {
            verificationCodePort.verifyPhoneCode(account, command.code(), LOGIN_PURPOSE);
        }

        // 防枚举：验码通过但账号不存在 → 与错码同一 CODE_INVALID
        Account found = isEmail
            ? accountRepository.findByEmail(account).orElse(null)
            : accountRepository.findByPhone(account).orElse(null);
        if (found == null) {
            throw new DomainException(VerificationCodeError.CODE_INVALID);
        }

        // 身份已证明——告知停用/锁定不构成枚举
        found.ensureLoginable();
        found.recordLogin();
        accountRepository.save(found);

        return loginCompletion.completeLogin(found, client, command.redirectUri(),
            command.nonce(), command.scope(), command.state());
    }
}
