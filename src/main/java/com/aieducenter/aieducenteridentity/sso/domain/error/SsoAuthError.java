package com.aieducenter.aieducenteridentity.sso.domain.error;

import com.cartisan.core.exception.CodeMessage;

/**
 * SSO 认证类错误码——{@code DomainException} 形态，给 {@code /api/sso/register}、
 * {@code /api/sso/login-code} 等「内部 {@code ApiResponse}」响应用。
 *
 * <p>区别于同包 {@link SsoError}：后者是 OIDC 标准 {@code {error, error_description}} 语料
 * （给 {@code /authorize}、{@code /token}、{@code /userinfo}，由 {@link OidcExceptionHandler} 转响应）。
 * register / login-code 失败走的是 {@code DomainException → ApiResponse} 400 形态，OIDC 语料不适用，
 * 故单立本枚举。</p>
 *
 * <h3>为何 sso 自有、不引 verification 的 {@code VerificationCodeError}</h3>
 * <p>ADR-0007：跨上下文只走应用层，不引对方 domain 错误。验码失败本由 verification 抛、经
 * {@code SsoVerificationCodeAdapter} 以 {@code DomainException} 形态透传——sso 不引用其错误码类；
 * 而 sso 自家抛错点（register 必填码兜底、login-code 账号不存在翻译）必须用 sso 自有码，
 * 否则 sso 对外 HTTP 契约（code/message/status）就被绑死在 verification 的 domain 定义上、丢了对自家契约的控制权。</p>
 *
 * <h3>防用户枚举不变量</h3>
 * <p>login-code 路径要求「错码」与「账号不存在」回<b>完全相同</b>的 code + message + status。两者都映射到
 * {@link #CODE_INVALID}——错码由 verification 抛后透传、账号不存在由 {@code authenticateByIdentifier} 抛
 * {@code ApplicationException} 后翻译——由 sso 自保两分支同 code，不依赖跨上下文复用对方错误码。</p>
 *
 * <p>注：{@link #CODE_INVALID} 的 code 串 {@code VERIFICATION_CODE_INVALID} 与原 verification 定义逐字符一致，
 * 前端契约无感；仅<b>归属</b>由 verification 收回 sso。</p>
 *
 * @since 0.1.0
 */
public enum SsoAuthError implements CodeMessage {

    /** 验证码错误（register 必填码兜底 / login-code 错码透传 / 账号不存在翻译——三处同此码，防枚举）。 */
    CODE_INVALID("VERIFICATION_CODE_INVALID", "验证码错误", 400);

    private final String code;
    private final String message;
    private final int httpStatus;

    SsoAuthError(String code, String message, int httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String message() {
        return message;
    }

    @Override
    public int httpStatus() {
        return httpStatus;
    }
}
