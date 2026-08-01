package com.aieducenter.aieducenteridentity.sso.application;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aieducenter.aieducenteridentity.account.domain.aggregate.Account;
import com.aieducenter.aieducenteridentity.account.domain.aggregate.Profile;
import com.aieducenter.aieducenteridentity.account.domain.repository.AccountRepository;
import com.aieducenter.aieducenteridentity.account.domain.repository.ProfileRepository;
import com.aieducenter.aieducenteridentity.sso.application.dto.SsoLoginResult;
import com.aieducenter.aieducenteridentity.sso.config.SsoProperties;
import com.aieducenter.aieducenteridentity.sso.domain.client.SsoClient;
import com.aieducenter.aieducenteridentity.sso.domain.client.SsoClientValidationService;
import com.aieducenter.aieducenteridentity.sso.domain.session.SsoSession;
import com.aieducenter.aieducenteridentity.sso.domain.session.SsoSessionRepository;

/**
 * dev 一键登应用服务（issue #16）——镜像 {@link SsoLoginAppService#loginByPassword}，但免认证、
 * 直接登 {@code SsoProperties.devLogin.accountEmail} 指向的预置测试账号。
 *
 * <p>仅在 {@code identity.sso.dev-login.enabled=true} 时注册（identity-web 登录页缺席时兜底）。
 * 仍走「建会话 → 发 code」正常流程，不直接发 token。</p>
 */
@Service
@ConditionalOnProperty(prefix = "identity.sso.dev-login", name = "enabled", havingValue = "true")
public class DevLoginAppService {

    private final SsoClientValidationService clientValidation;
    private final SsoSessionRepository sessionRepository;
    private final AuthorizationCodeAppService codeService;
    private final AccountRepository accountRepository;
    private final ProfileRepository profileRepository;
    private final SsoProperties properties;

    public DevLoginAppService(SsoClientValidationService clientValidation, SsoSessionRepository sessionRepository,
            AuthorizationCodeAppService codeService, AccountRepository accountRepository,
            ProfileRepository profileRepository, SsoProperties properties) {
        this.clientValidation = clientValidation;
        this.sessionRepository = sessionRepository;
        this.codeService = codeService;
        this.accountRepository = accountRepository;
        this.profileRepository = profileRepository;
        this.properties = properties;
    }

    /**
     * 以预置测试账号建 SSO 会话 + 发 code。
     *
     * @throws com.aieducenter.aieducenteridentity.sso.domain.error.OidcException client/redirect_uri 无效（400）
     * @throws com.cartisan.core.exception.DomainException 预置账号停用/锁定（ACCOUNT_DISABLED/LOCKED）
     */
    @Transactional
    public SsoLoginResult loginAsDevAccount(String clientId, String redirectUri, String state, String nonce) {
        SsoClient client = clientValidation.requireActiveClient(clientId);
        clientValidation.requireRedirectUri(client, redirectUri);

        Account account = accountRepository.findByEmail(properties.getDevLogin().getAccountEmail())
            .orElseThrow(() -> new IllegalStateException(
                "dev 测试账号未种：" + properties.getDevLogin().getAccountEmail()));
        account.ensureLoginable();
        account.recordLogin();
        accountRepository.save(account);

        Profile profile = profileRepository.findById(account.getId()).orElse(null);
        String nickname = profile != null ? profile.getNickname() : null;
        SsoSession session = sessionRepository.create(account.getId(), account.displayLabel(nickname));
        String redirectUrl = codeService.issueCodeAndRedirect(
            account.getId(), client, redirectUri, nonce, null, session.sessionId(), state);
        return new SsoLoginResult(session.sessionId(), redirectUrl);
    }
}
