package com.aieducenter.aieducenteridentity.sso.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aieducenter.aieducenteridentity.account.domain.aggregate.Account;
import com.aieducenter.aieducenteridentity.account.domain.error.AccountError;
import com.aieducenter.aieducenteridentity.account.domain.repository.AccountRepository;
import com.aieducenter.aieducenteridentity.account.domain.service.AccountPasswordEncoderService;
import com.aieducenter.aieducenteridentity.sso.application.dto.RegisterByPasswordSsoCommand;
import com.aieducenter.aieducenteridentity.sso.application.dto.SsoLoginResult;
import com.aieducenter.aieducenteridentity.sso.domain.client.SsoClient;
import com.aieducenter.aieducenteridentity.sso.domain.client.SsoClientValidationService;
import com.aieducenter.aieducenteridentity.sso.domain.error.OidcException;
import com.aieducenter.aieducenteridentity.sso.domain.session.SsoSession;
import com.aieducenter.aieducenteridentity.sso.domain.session.SsoSessionRepository;
import com.cartisan.core.exception.DomainException;

/**
 * /api/auth/register 密码注册（CONTEXT 注册 / issue #18）。
 *
 * <p>校验 client/redirect_uri → 唯一性（email/phone 全局唯一，重复 409 不建第二个号）→ 建号
 * （密码 hash 落库，联络方式格式由 {@link Account#register} 不变量校验）→ 注册即登录：
 * 建 SSO 会话 → 发 code（与 {@link SsoLoginAppService} 同一后半段、共用发码路径）。</p>
 *
 * <p>注册失败（重复/格式错）抛 {@link DomainException}（留注册页显示，不回业务应用）；
 * client/redirect_uri 无效抛 {@link OidcException}（不重定向，与 login 一致）。</p>
 *
 * @since 0.1.0
 */
@Service
public class SsoRegisterAppService {

    private final SsoClientValidationService clientValidation;
    private final SsoSessionRepository sessionRepository;
    private final AuthorizationCodeAppService codeService;
    private final AccountRepository accountRepository;
    private final AccountPasswordEncoderService passwordEncoderService;

    public SsoRegisterAppService(SsoClientValidationService clientValidation, SsoSessionRepository sessionRepository,
            AuthorizationCodeAppService codeService, AccountRepository accountRepository,
            AccountPasswordEncoderService passwordEncoderService) {
        this.clientValidation = clientValidation;
        this.sessionRepository = sessionRepository;
        this.codeService = codeService;
        this.accountRepository = accountRepository;
        this.passwordEncoderService = passwordEncoderService;
    }

    /**
     * 密码注册 → 建号 → 建 SSO 会话 + 发 code（注册即登录）。
     *
     * @throws OidcException   client/redirect_uri 无效（400，不重定向）
     * @throws DomainException CONTACT_REQUIRED / EMAIL_INVALID / PHONE_INVALID（400）；
     *                         EMAIL_ALREADY_EXISTS / PHONE_ALREADY_EXISTS（409）
     */
    @Transactional
    public SsoLoginResult registerByPassword(RegisterByPasswordSsoCommand command) {
        SsoClient client = clientValidation.requireActiveClient(command.clientId());
        clientValidation.requireRedirectUri(client, command.redirectUri());

        String email = normalizeContact(command.email());
        String phone = normalizeContact(command.phone());
        if (email != null && accountRepository.existsByEmail(email)) {
            throw new DomainException(AccountError.EMAIL_ALREADY_EXISTS);
        }
        if (phone != null && accountRepository.existsByPhone(phone)) {
            throw new DomainException(AccountError.PHONE_ALREADY_EXISTS);
        }

        Account account = Account.register(email, phone, passwordEncoderService.encodePassword(command.password()));
        account.recordLogin();
        accountRepository.save(account);

        SsoSession session = sessionRepository.create(account.getId(), account.displayLabel(null));
        String redirectUrl = codeService.issueCodeAndRedirect(
            account.getId(), client, command.redirectUri(), command.nonce(), command.scope(),
            session.sessionId(), command.state());
        return new SsoLoginResult(session.sessionId(), redirectUrl);
    }

    /** 归一联络方式：去空白，空串归 null（email/phone 均可空，空串等同未填）。 */
    private static String normalizeContact(String contact) {
        if (contact == null || contact.isBlank()) {
            return null;
        }
        return contact.trim();
    }
}
