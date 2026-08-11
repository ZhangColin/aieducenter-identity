package com.aieducenter.aieducenteridentity.sso.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aieducenter.aieducenteridentity.account.application.AccountAuthAppService;
import com.aieducenter.aieducenteridentity.account.application.dto.response.SubjectView;
import com.aieducenter.aieducenteridentity.sso.application.dto.LoginByPasswordSsoCommand;
import com.aieducenter.aieducenteridentity.sso.application.dto.SsoLoginResult;
import com.aieducenter.aieducenteridentity.sso.domain.client.SsoClient;
import com.aieducenter.aieducenteridentity.sso.domain.client.SsoClientValidationService;
import com.aieducenter.aieducenteridentity.sso.domain.error.OidcException;
import com.cartisan.core.exception.DomainException;

/**
 * /api/auth/login 密码登录（CONTEXT 登录契约 / issue #15）。
 *
 * <p>校验 client/redirect_uri → 密码认证（经 {@link AccountAuthAppService#authenticate}：账号定位 +
 * 密码校验 + 停用/锁定 + recordLogin，返 {@link SubjectView}）→ {@link SsoLoginCompletionAppService 统一后半段}：
 * 建 SSO 会话 → 发 code。失败（凭据错/锁定）抛 {@link DomainException}（留登录页显示，不回业务应用）。</p>
 *
 * <h3>防用户枚举</h3>
 * <p>账号不存在与密码错误由 {@code AccountAuthAppService.authenticate} 统一抛
 * {@code LOGIN_PASSWORD_INCORRECT}（同一 code+message），不暴露账号存在性——本服务不再直穿 account domain
 * 做定位/校验（ADR-0007）。</p>
 *
 * @since 0.1.0
 */
@Service
public class SsoLoginAppService {

    private final SsoClientValidationService clientValidation;
    private final AccountAuthAppService accountAuth;
    private final SsoLoginCompletionAppService loginCompletion;

    public SsoLoginAppService(SsoClientValidationService clientValidation, AccountAuthAppService accountAuth,
            SsoLoginCompletionAppService loginCompletion) {
        this.clientValidation = clientValidation;
        this.accountAuth = accountAuth;
        this.loginCompletion = loginCompletion;
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

        SubjectView subject = accountAuth.authenticate(command.account(), command.password());

        return loginCompletion.completeLogin(subject, client, command.redirectUri(),
            command.nonce(), command.scope(), command.state());
    }
}
