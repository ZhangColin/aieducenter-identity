package com.aieducenter.aieducenteridentity.sso.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aieducenter.aieducenteridentity.account.application.AccountAuthAppService;
import com.aieducenter.aieducenteridentity.account.application.dto.response.SubjectView;
import com.aieducenter.aieducenteridentity.sso.application.dto.LoginByCodeSsoCommand;
import com.aieducenter.aieducenteridentity.sso.application.dto.SsoLoginResult;
import com.aieducenter.aieducenteridentity.sso.domain.client.SsoClient;
import com.aieducenter.aieducenteridentity.sso.domain.client.SsoClientValidationService;
import com.aieducenter.aieducenteridentity.sso.domain.error.OidcException;
import com.aieducenter.aieducenteridentity.shared.error.SharedErrorCode;
import com.aieducenter.aieducenteridentity.sso.infrastructure.verification.VerificationCodePort;
import com.cartisan.core.exception.ApplicationException;
import com.cartisan.core.exception.DomainException;

/**
 * /api/sso/login-code 验证码登录（CONTEXT 登录契约 / issue #22）。
 *
 * <p>校验 client/redirect_uri → 验码（purpose=LOGIN，经 verification 上下文）→
 * 凭已验证联络方式认证（经 {@link AccountAuthAppService#authenticateByIdentifier}：定位账号 + 停用/锁定 +
 * recordLogin，返 {@link SubjectView}）→ {@link SsoLoginCompletionAppService 统一后半段}：建 SSO 会话 → 发 code。
 * 不设密码的账号（注册时密码可选）由此入口登录。</p>
 *
 * <h3>防用户枚举</h3>
 * <p>「账号不存在」与「验证码错误」同一响应：错码由 verification 抛 {@code DomainException}（经 ACL port 透传，
 * sso 不引其错误码类）；验码通过但账号不存在时，{@code authenticateByIdentifier} 抛
 * {@code ApplicationException(ACCOUNT_NOT_FOUND)}，本服务翻译为同一 {@link SharedErrorCode#VERIFICATION_CODE_INVALID}
 * （同一 code+message+status，不暴露账号存在性）。停用/锁定是 {@code DomainException}（身份已证明后才告知，
 * 不构成枚举），原样向上抛。</p>
 *
 * @since 0.1.0
 */
@Service
public class SsoLoginCodeAppService {

    /** 验证码用途：登录（与注册的 REGISTER 分键，互不串用；字面量需与 verification 枚举名一致，经 ACL port 传 String）。 */
    private static final String LOGIN_PURPOSE = "LOGIN";

    private final SsoClientValidationService clientValidation;
    private final AccountAuthAppService accountAuth;
    private final VerificationCodePort verificationCodePort;
    private final SsoLoginCompletionAppService loginCompletion;

    public SsoLoginCodeAppService(SsoClientValidationService clientValidation, AccountAuthAppService accountAuth,
            VerificationCodePort verificationCodePort, SsoLoginCompletionAppService loginCompletion) {
        this.clientValidation = clientValidation;
        this.accountAuth = accountAuth;
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

        // 防枚举：验码通过但账号不存在 → 与错码同一 VERIFICATION_CODE_INVALID（shared 契约码，见 SharedErrorCode）。
        // authenticateByIdentifier 仅在账号定位不到时抛 ApplicationException(ACCOUNT_NOT_FOUND)；
        // 停用/锁定是 DomainException，不在此处处理——原样向上抛（身份已证明，不构成枚举）。
        SubjectView subject;
        try {
            subject = accountAuth.authenticateByIdentifier(account);
        } catch (ApplicationException e) {
            throw new DomainException(SharedErrorCode.VERIFICATION_CODE_INVALID);
        }

        return loginCompletion.completeLogin(subject, client, command.redirectUri(),
            command.nonce(), command.scope(), command.state());
    }
}
