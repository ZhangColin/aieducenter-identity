package com.aieducenter.aieducenteridentity.account.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aieducenter.aieducenteridentity.account.application.dto.command.RegisterAccountCommand;
import com.aieducenter.aieducenteridentity.account.application.dto.command.RegisterByCodeCommand;
import com.aieducenter.aieducenteridentity.account.application.dto.response.SubjectView;
import com.aieducenter.aieducenteridentity.account.domain.aggregate.Account;
import com.aieducenter.aieducenteridentity.account.domain.aggregate.Profile;
import com.aieducenter.aieducenteridentity.account.domain.error.AccountError;
import com.aieducenter.aieducenteridentity.account.domain.repository.AccountRepository;
import com.aieducenter.aieducenteridentity.account.domain.repository.ProfileRepository;
import com.aieducenter.aieducenteridentity.account.domain.service.AccountPasswordEncoderService;
import com.aieducenter.aieducenteridentity.account.infrastructure.verification.VerificationCodePort;
import com.cartisan.core.exception.ApplicationException;
import com.cartisan.core.exception.DomainException;

/**
 * 账号认证 / 建号应用服务——account 上下文给 sso（及签名服务）的「认人 / 建号」契约缝（ADR-0007/0008）。
 *
 * <p>把「定位 → 凭据校验 → 账号状态机（{@code ensureLoginable} / {@code recordLogin}）→ save」内聚成应用层方法，
 * 返回统一读模型 {@link SubjectView}。sso 不再直穿 account domain（注入仓储 / 拿聚合 / 调域服务 / 抛域错误），
 * 只经本面与 account 交往。</p>
 *
 * <h3>验码：两套入口</h3>
 * <p>验码归 verification 上下文，但「谁去验」按调用方分两套入口：</p>
 * <ul>
 *   <li><b>信任调用方</b>（{@code authenticateByIdentifier} / {@code register}）：不验码——调用方（sso 等）
 *       自验过联络方式再调本方法（与 {@code Account.register} 契约「由调用方保证已当场发码验证」一致）。</li>
 *   <li><b>account 自验码</b>（{@code authenticateByCode} / {@code registerByCode}，签名服务 #59）：
 *       入参带 {@code code}，本服务经 {@link VerificationCodePort} 当场验码再委托上面两个信任入口。
 *       供签名服务（{@code /api/account/*}，调用方只持 apiKey、不自己跑 verification 闭环）。</li>
 * </ul>
 * <p>两套入口共享同一领域逻辑（定位 / 状态机 / 建号唯一性），验码只是签名入口的前置 gate——领域逻辑不重写、不重测。</p>
 *
 * <h3>事务边界</h3>
 * <p>本类整块为 account 自己的 {@code @Transactional}——凭据校验 + 状态机 + save 在同一事务内，
 * 与未来 sso 建会话事务分离（更正确：account 写库与会话建立各自独立提交）。</p>
 *
 * @since 0.1.0
 */
@Service
@Transactional
public class AccountAuthAppService {

    /** 验证码用途：登录（字面量需与 verification 枚举 {@code VerificationPurpose.LOGIN} 一致，经 ACL port 传 String）。 */
    private static final String LOGIN_PURPOSE = "LOGIN";
    /** 验证码用途：注册（与登录的 LOGIN 分键，互不串用；字面量需与 {@code VerificationPurpose.REGISTER} 一致）。 */
    private static final String REGISTER_PURPOSE = "REGISTER";

    private final AccountRepository accountRepository;
    private final AccountPasswordEncoderService passwordEncoderService;
    private final ProfileRepository profileRepository;
    private final VerificationCodePort verificationCodePort;

    public AccountAuthAppService(AccountRepository accountRepository,
            AccountPasswordEncoderService passwordEncoderService, ProfileRepository profileRepository,
            VerificationCodePort verificationCodePort) {
        this.accountRepository = accountRepository;
        this.passwordEncoderService = passwordEncoderService;
        this.profileRepository = profileRepository;
        this.verificationCodePort = verificationCodePort;
    }

    /**
     * 密码认证——定位(email/phone) → 验码 → {@code ensureLoginable} → {@code recordLogin} → save → {@link SubjectView}。
     *
     * <h3>防用户枚举</h3>
     * 账号不存在与密码错误统一抛 {@link AccountError#LOGIN_PASSWORD_INCORRECT}（同一 code+message+status），
     * 不暴露账号存在性。停用/锁定（{@link AccountError#ACCOUNT_DISABLED}/{@link AccountError#ACCOUNT_LOCKED}）
     * 在密码通过（身份已证明）后才告知，不构成枚举。
     *
     * @param identifier 邮箱或手机号（用户输入）
     * @param password   明文密码
     * @return 已认证 subject 读模型（含可用性 status）
     * @throws DomainException LOGIN_PASSWORD_INCORRECT（401，账号不存在/密码错统一）/ ACCOUNT_DISABLED / ACCOUNT_LOCKED
     */
    public SubjectView authenticate(String identifier, String password) {
        // 防枚举：账号不存在与密码错误统一——先定位，定位不到或验不过都抛同一 LOGIN_PASSWORD_INCORRECT
        Account account = accountRepository.findByEmail(identifier)
            .or(() -> accountRepository.findByPhone(identifier))
            .orElse(null);
        if (account == null
            || !passwordEncoderService.verifyPassword(password, account.getPasswordHash())) {
            throw new DomainException(AccountError.LOGIN_PASSWORD_INCORRECT);
        }
        // 身份已证明——告知停用/锁定不构成枚举
        account.ensureLoginable();
        account.recordLogin();
        accountRepository.save(account);
        return subjectView(account);
    }

    /**
     * 凭已验证联络方式认证（验证码登录路径，无密码）——定位(email/phone) → {@code ensureLoginable} →
     * {@code recordLogin} → save → {@link SubjectView}。
     *
     * <p>调用方需<b>先经 verification 上下文验过码</b>再调本方法。账号定位不到抛
     * {@link AccountError#ACCOUNT_NOT_FOUND}（{@code ApplicationException}，401）——此时调用方掌控的联络方式
     * 已验码通过，由调用方自决是否翻译为防枚举响应（sso login-code 翻译为与错码同一的 CODE_INVALID）。</p>
     *
     * @param identifier 已验码通过的邮箱或手机号
     * @return 已认证 subject 读模型（含可用性 status）
     * @throws ApplicationException ACCOUNT_NOT_FOUND（401，验码通过但账号不存在）
     * @throws DomainException      ACCOUNT_DISABLED / ACCOUNT_LOCKED（身份已证明后才告知）
     */
    public SubjectView authenticateByIdentifier(String identifier) {
        String contact = identifier.trim();
        boolean isEmail = contact.contains("@");
        Account account = isEmail
            ? accountRepository.findByEmail(contact).orElse(null)
            : accountRepository.findByPhone(contact).orElse(null);
        if (account == null) {
            throw new ApplicationException(AccountError.ACCOUNT_NOT_FOUND);
        }
        // 身份已证明（调用方验码通过）——告知停用/锁定不构成枚举
        account.ensureLoginable();
        account.recordLogin();
        accountRepository.save(account);
        return subjectView(account);
    }

    /**
     * 建号——唯一性 → 密码可选 encode → {@code Account.register} → {@code recordLogin} → save → {@link SubjectView}。
     *
     * <p>「注册即登录」：建号即记登录态（{@code recordLogin}），调用方凭返回的 {@link SubjectView} 直接建会话。
     * 联络方式唯一性（email/phone 全局唯一，重复 409 不建第二个号）+ 格式校验（收在 {@link Account#register} 聚合不变量）。
     * 密码可空（不设密码的账号后续用验证码登）。<b>不做验码</b>——调用方先验过码再调本方法。</p>
     *
     * @param command 建号命令（email/phone 至少其一 + 可选密码；联络方式需调用方已验码）
     * @return 新建账号的 subject 读模型（新号尚无 Profile，nickname/avatar 为 null）
     * @throws DomainException EMAIL_ALREADY_EXISTS / PHONE_ALREADY_EXISTS（409）；
     *                         CONTACT_REQUIRED / EMAIL_INVALID / PHONE_INVALID（400，聚合不变量）
     */
    public SubjectView register(RegisterAccountCommand command) {
        String email = normalizeContact(command.email());
        String phone = normalizeContact(command.phone());
        if (email != null && accountRepository.existsByEmail(email)) {
            throw new DomainException(AccountError.EMAIL_ALREADY_EXISTS);
        }
        if (phone != null && accountRepository.existsByPhone(phone)) {
            throw new DomainException(AccountError.PHONE_ALREADY_EXISTS);
        }

        // 密码可选（不设密码的账号后续用验证码登）
        String passwordHash = (command.password() != null && !command.password().isBlank())
            ? passwordEncoderService.encodePassword(command.password())
            : null;
        Account account = Account.register(email, phone, passwordHash);
        account.recordLogin();
        accountRepository.save(account);
        // 新号尚无 Profile（Profile 由资料编辑 / 社交登录另建）——nickname/avatar 为 null
        return SubjectView.of(account, null);
    }

    /**
     * 验证码登录（签名服务入口，#59）——验码（purpose=LOGIN）→ 委托 {@link #authenticateByIdentifier}。
     *
     * <p>与 {@code authenticateByIdentifier}（信任调用方已验码）的区别：本方法入参带 {@code code}，
     * account 自验码（经 {@link VerificationCodePort}）。验码失败抛 {@code DomainException}（CODE_INVALID 等，
     * 经 ACL port 透传 verification 错误码）；验过后委托既有 {@code authenticateByIdentifier}——
     * 定位 / 状态机 / recordLogin 领域逻辑不重写。</p>
     *
     * @param identifier 邮箱或手机号（含 {@code @} 走邮箱验码、否则手机）
     * @param code       验证码
     * @return 已认证 subject 读模型
     * @throws DomainException CODE_INVALID / CODE_EXPIRED / CODE_ALREADY_USED（验码失败）；
     *                         ACCOUNT_DISABLED / ACCOUNT_LOCKED（身份已证明后才告知）
     * @throws ApplicationException ACCOUNT_NOT_FOUND（验码通过但账号不存在）
     */
    public SubjectView authenticateByCode(String identifier, String code) {
        verifyContactCode(identifier, code, LOGIN_PURPOSE);
        return authenticateByIdentifier(identifier);
    }

    /**
     * 验证码注册（签名服务入口，#59）——验码（purpose=REGISTER，提供的联络方式各验各的码）→ 委托 {@link #register}。
     *
     * <p>与 {@code register}（信任调用方已验码）的区别：本方法入参带 {@code code}，account 自验码。
     * <b>验不过不建号</b>（防占用他人联络方式）；验过后委托既有 {@code register}——唯一性 / 密码 encode /
     * 聚合不变量领域逻辑不重写。email/phone 至少其一（都没给则不验码、交 {@code register} 抛 CONTACT_REQUIRED）；
     * 两者都给时验 email 的码。</p>
     *
     * @param command 建号命令（email/phone 至少其一 + code + 可选密码）
     * @return 新建账号的 subject 读模型
     * @throws DomainException CONTACT_REQUIRED / EMAIL_INVALID / PHONE_INVALID / CODE_INVALID（400）；
     *                         EMAIL_ALREADY_EXISTS / PHONE_ALREADY_EXISTS（409）
     */
    public SubjectView registerByCode(RegisterByCodeCommand command) {
        verifyRegisterCode(command.email(), command.phone(), command.code());
        return register(new RegisterAccountCommand(command.email(), command.phone(), command.password()));
    }

    /**
     * 验联络方式验证码：identifier 含 {@code @} 走邮箱、否则走手机（与 {@code authenticateByIdentifier} 口径一致）。
     */
    private void verifyContactCode(String identifier, String code, String purpose) {
        String contact = identifier.trim();
        if (contact.contains("@")) {
            verificationCodePort.verifyCode(contact, code, purpose);
        } else {
            verificationCodePort.verifyPhoneCode(contact, code, purpose);
        }
    }

    /**
     * 注册验码：提供的联络方式各验各的码——email 优先；都没给则不验（交 {@code register} 抛 CONTACT_REQUIRED）。
     */
    private void verifyRegisterCode(String email, String phone, String code) {
        if (email != null && !email.isBlank()) {
            verificationCodePort.verifyCode(email.trim(), code, REGISTER_PURPOSE);
        } else if (phone != null && !phone.isBlank()) {
            verificationCodePort.verifyPhoneCode(phone.trim(), code, REGISTER_PURPOSE);
        }
    }

    /**
     * 装载 Profile 并投影出 SubjectView（authenticate / authenticateByIdentifier 共用）。
     */
    private SubjectView subjectView(Account account) {
        Profile profile = profileRepository.findById(account.getId()).orElse(null);
        return SubjectView.of(account, profile);
    }

    /** 归一联络方式：去空白，空串归 null（email/phone 均可空，空串等同未填）。 */
    private static String normalizeContact(String contact) {
        if (contact == null || contact.isBlank()) {
            return null;
        }
        return contact.trim();
    }
}
