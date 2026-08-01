package com.aieducenter.aieducenteridentity.sso.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aieducenter.aieducenteridentity.account.domain.aggregate.Account;
import com.aieducenter.aieducenteridentity.account.domain.aggregate.Profile;
import com.aieducenter.aieducenteridentity.account.domain.error.AccountError;
import com.aieducenter.aieducenteridentity.account.domain.repository.AccountRepository;
import com.aieducenter.aieducenteridentity.account.domain.repository.ProfileRepository;
import com.aieducenter.aieducenteridentity.account.domain.service.AccountPasswordEncoderService;
import com.aieducenter.aieducenteridentity.sso.application.dto.LoginByPasswordSsoCommand;
import com.aieducenter.aieducenteridentity.sso.application.dto.SsoLoginResult;
import com.aieducenter.aieducenteridentity.sso.domain.client.SsoClient;
import com.aieducenter.aieducenteridentity.sso.domain.client.SsoClientValidationService;
import com.aieducenter.aieducenteridentity.sso.domain.error.OidcException;
import com.aieducenter.aieducenteridentity.sso.domain.session.SsoSession;
import com.aieducenter.aieducenteridentity.sso.domain.session.SsoSessionRepository;
import com.cartisan.core.exception.DomainException;

/**
 * /api/auth/login 密码登录（CONTEXT 登录契约 / issue #15）。
 *
 * <p>校验 client/redirect_uri → 密码认证（复用 account 上下文：账号定位 + 密码校验 + 停用/锁定）→
 * 建 SSO 会话 → 发 code。失败（凭据错/锁定）抛 {@link DomainException}（留登录页显示，不回业务应用）。</p>
 *
 * <h3>防用户枚举</h3>
 * <p>账号不存在与密码错误统一抛 {@link AccountError#LOGIN_PASSWORD_INCORRECT}（同一 code+message），
 * 不暴露账号存在性。停用/锁定在密码通过后才告知。</p>
 *
 * @since 0.1.0
 */
@Service
public class SsoLoginAppService {

    private final SsoClientValidationService clientValidation;
    private final SsoSessionRepository sessionRepository;
    private final AuthorizationCodeAppService codeService;
    private final AccountRepository accountRepository;
    private final ProfileRepository profileRepository;
    private final AccountPasswordEncoderService passwordEncoderService;

    public SsoLoginAppService(SsoClientValidationService clientValidation, SsoSessionRepository sessionRepository,
            AuthorizationCodeAppService codeService, AccountRepository accountRepository,
            ProfileRepository profileRepository, AccountPasswordEncoderService passwordEncoderService) {
        this.clientValidation = clientValidation;
        this.sessionRepository = sessionRepository;
        this.codeService = codeService;
        this.accountRepository = accountRepository;
        this.profileRepository = profileRepository;
        this.passwordEncoderService = passwordEncoderService;
    }

    /**
     * 密码登录 → 建 SSO 会话 + 发 code。
     *
     * @throws OidcException  client/redirect_uri 无效（400）
     * @throws DomainException LOGIN_PASSWORD_INCORRECT（401，账号不存在/密码错统一）/ ACCOUNT_DISABLED / ACCOUNT_LOCKED
     */
    @Transactional
    public SsoLoginResult loginByPassword(LoginByPasswordSsoCommand command) {
        SsoClient client = clientValidation.requireActiveClient(command.clientId());
        clientValidation.requireRedirectUri(client, command.redirectUri());

        // 防枚举：账号不存在与密码错误统一
        Account account = accountRepository.findByEmail(command.account())
            .or(() -> accountRepository.findByPhone(command.account()))
            .orElse(null);
        if (account == null
            || !passwordEncoderService.verifyPassword(command.password(), account.getPasswordHash())) {
            throw new DomainException(AccountError.LOGIN_PASSWORD_INCORRECT);
        }
        // 身份已证明——告知停用/锁定不构成枚举
        account.ensureLoginable();
        account.recordLogin();
        accountRepository.save(account);

        Profile profile = profileRepository.findById(account.getId()).orElse(null);
        String nickname = profile != null ? profile.getNickname() : null;
        SsoSession session = sessionRepository.create(account.getId(), account.displayLabel(nickname));
        String redirectUrl = codeService.issueCodeAndRedirect(
            account.getId(), client, command.redirectUri(), command.nonce(), null,
            session.sessionId(), command.state());
        return new SsoLoginResult(session.sessionId(), redirectUrl);
    }
}
