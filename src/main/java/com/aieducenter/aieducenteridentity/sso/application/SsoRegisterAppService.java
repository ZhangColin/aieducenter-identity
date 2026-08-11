package com.aieducenter.aieducenteridentity.sso.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aieducenter.aieducenteridentity.account.application.AccountAuthAppService;
import com.aieducenter.aieducenteridentity.account.application.dto.command.RegisterAccountCommand;
import com.aieducenter.aieducenteridentity.account.application.dto.response.SubjectView;
import com.aieducenter.aieducenteridentity.sso.application.dto.RegisterSsoCommand;
import com.aieducenter.aieducenteridentity.sso.application.dto.SsoLoginResult;
import com.aieducenter.aieducenteridentity.sso.domain.client.SsoClient;
import com.aieducenter.aieducenteridentity.sso.domain.client.SsoClientValidationService;
import com.aieducenter.aieducenteridentity.sso.domain.error.OidcException;
import com.aieducenter.aieducenteridentity.sso.domain.error.SsoAuthError;
import com.aieducenter.aieducenteridentity.sso.infrastructure.verification.VerificationCodePort;
import com.cartisan.core.exception.DomainException;

/**
 * /api/auth/register 注册（CONTEXT 注册 / issue #18、#22）。
 *
 * <p>校验 client/redirect_uri → 当场验码（提供的联络方式各验各的码，purpose=REGISTER，<b>验不过不建号</b>）→
 * 建号（经 {@link AccountAuthAppService#register}：唯一性 + 密码可选 encode + {@code Account.register} +
 * recordLogin，返 {@link SubjectView}；联络方式格式由聚合不变量校验）→ 注册即登录：
 * {@link SsoLoginCompletionAppService 统一后半段}（与 login 同一发码契约）。</p>
 *
 * <p>注：唯一性检查收在 {@code AccountAuthAppService.register} 内，<b>在验码之后</b>——
 * 故重复联络方式会先消耗验证码再抛 {@code EMAIL_ALREADY_EXISTS}/{@code PHONE_ALREADY_EXISTS}（409）。
 * 这反而收紧了防枚举：未验码者拿不到存在性信息。注册失败抛 {@link DomainException}（留注册页显示，不回业务应用）；
 * client/redirect_uri 无效抛 {@link OidcException}（不重定向，与 login 一致）。</p>
 *
 * @since 0.1.0
 */
@Service
public class SsoRegisterAppService {

    /** 验证码用途：注册（与登录的 LOGIN 分键，互不串用；字面量需与 verification 枚举名一致，经 ACL port 传 String）。 */
    private static final String REGISTER_PURPOSE = "REGISTER";

    private final SsoClientValidationService clientValidation;
    private final AccountAuthAppService accountAuth;
    private final VerificationCodePort verificationCodePort;
    private final SsoLoginCompletionAppService loginCompletion;

    public SsoRegisterAppService(SsoClientValidationService clientValidation, AccountAuthAppService accountAuth,
            VerificationCodePort verificationCodePort, SsoLoginCompletionAppService loginCompletion) {
        this.clientValidation = clientValidation;
        this.accountAuth = accountAuth;
        this.verificationCodePort = verificationCodePort;
        this.loginCompletion = loginCompletion;
    }

    /**
     * 注册 → 当场验码 → 建号 → 建 SSO 会话 + 发 code（注册即登录）。
     *
     * @throws OidcException   client/redirect_uri 无效（400，不重定向）
     * @throws DomainException CONTACT_REQUIRED / EMAIL_INVALID / PHONE_INVALID / CODE_INVALID（400，验不过不建号）；
     *                         EMAIL_ALREADY_EXISTS / PHONE_ALREADY_EXISTS（409）
     */
    @Transactional
    public SsoLoginResult register(RegisterSsoCommand command) {
        SsoClient client = clientValidation.requireActiveClient(command.clientId());
        clientValidation.requireRedirectUri(client, command.redirectUri());

        String email = normalizeContact(command.email());
        String phone = normalizeContact(command.phone());

        // 当场验码：提供的联络方式各验各的码——未验的联络方式不落库（防占用他人联络方式）
        if (email != null) {
            verificationCodePort.verifyCode(email, requireCode(command.emailCode()), REGISTER_PURPOSE);
        }
        if (phone != null) {
            verificationCodePort.verifyPhoneCode(phone, requireCode(command.phoneCode()), REGISTER_PURPOSE);
        }

        // 建号收在 account 应用层：唯一性 + 密码可选 encode + 聚合不变量 + recordLogin，返 SubjectView
        SubjectView subject = accountAuth.register(new RegisterAccountCommand(email, phone, command.password()));

        return loginCompletion.completeLogin(subject, client, command.redirectUri(),
            command.nonce(), command.scope(), command.state());
    }

    /** 归一联络方式：去空白，空串归 null（email/phone 均可空，空串等同未填）。 */
    private static String normalizeContact(String contact) {
        if (contact == null || contact.isBlank()) {
            return null;
        }
        return contact.trim();
    }

    /** 验证码必填兜底：空白等同错码（CODE_INVALID），不进 Redis 查询。 */
    private static String requireCode(String code) {
        if (code == null || code.isBlank()) {
            throw new DomainException(SsoAuthError.CODE_INVALID);
        }
        return code.trim();
    }
}
