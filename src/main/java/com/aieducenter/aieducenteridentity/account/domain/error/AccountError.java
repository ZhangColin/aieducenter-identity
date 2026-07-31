package com.aieducenter.aieducenteridentity.account.domain.error;

import com.cartisan.core.exception.CodeMessage;

/**
 * Account 上下文错误码。
 *
 * <h3>防用户枚举不变量</h3>
 * <p>密码登录时，账号不存在与密码错误统一抛 {@link #LOGIN_PASSWORD_INCORRECT}（同一 code + message），
 * 不暴露账号存在性。停用/锁定（{@link #ACCOUNT_DISABLED}/{@link #ACCOUNT_LOCKED}）在身份验证通过后才告知，
 * 不构成枚举。</p>
 *
 * @since 0.1.0
 */
public enum AccountError implements CodeMessage {

    // ========== 格式校验错误 (400) ==========

    /** 至少需要填写邮箱或手机号之一。 */
    CONTACT_REQUIRED(400, "ACCOUNT_001", "至少需要填写邮箱或手机号"),

    /** 邮箱格式不正确。 */
    EMAIL_INVALID(400, "ACCOUNT_002", "邮箱格式不正确"),

    /** 手机号格式不正确。 */
    PHONE_INVALID(400, "ACCOUNT_003", "手机号格式不正确"),

    /** 密码强度不足。 */
    PASSWORD_WEAK(400, "ACCOUNT_004", "密码强度不足"),

    /** 旧密码错误（修改密码场景）。 */
    PASSWORD_INCORRECT(400, "ACCOUNT_005", "旧密码错误"),

    /** 新密码不能与旧密码相同。 */
    PASSWORD_SAME_AS_OLD(400, "ACCOUNT_006", "新密码不能与旧密码相同"),

    // ========== 唯一性错误 (409) ==========

    /** 邮箱已被使用。 */
    EMAIL_ALREADY_EXISTS(409, "ACCOUNT_007", "邮箱已被使用"),

    /** 手机号已被使用。 */
    PHONE_ALREADY_EXISTS(409, "ACCOUNT_008", "手机号已被使用"),

    // ========== 登录错误 (401) ==========

    /** 账号或密码错误（账号不存在与密码错误统一，防用户枚举）。 */
    LOGIN_PASSWORD_INCORRECT(401, "ACCOUNT_009", "账号或密码错误"),

    /** 账号已停用。 */
    ACCOUNT_DISABLED(401, "ACCOUNT_010", "账号已停用"),

    /** 账号已锁定。 */
    ACCOUNT_LOCKED(401, "ACCOUNT_011", "账号已锁定"),

    /** 账号不存在（短信登录等已验证凭据后的兜底，返回 401）。 */
    ACCOUNT_NOT_FOUND(401, "ACCOUNT_012", "账号不存在"),

    /** refresh_token 无效或已过期（非法/已用/过期，/refresh 凭 refresh_token 公开鉴权）。 */
    REFRESH_TOKEN_INVALID(401, "ACCOUNT_014", "refresh_token 无效或已过期"),

    // ========== 资源不存在 (404) ==========

    /** 用户不存在（按登录态 userId 查询时的不一致兜底）。 */
    USER_NOT_FOUND(404, "ACCOUNT_013", "用户不存在");

    private final int httpStatus;
    private final String code;
    private final String message;

    AccountError(int httpStatus, String code, String message) {
        this.httpStatus = httpStatus;
        this.code = code;
        this.message = message;
    }

    @Override
    public int httpStatus() {
        return httpStatus;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String message() {
        return message;
    }
}
