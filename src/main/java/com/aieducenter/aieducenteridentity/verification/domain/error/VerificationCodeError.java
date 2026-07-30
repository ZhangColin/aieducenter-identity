package com.aieducenter.aieducenteridentity.verification.domain.error;

import com.cartisan.core.exception.CodeMessage;

/**
 * 验证码相关错误码。
 *
 * @since 0.1.0
 */
public enum VerificationCodeError implements CodeMessage {

    // ========== 业务错误 ==========

    /**
     * 邮箱格式不正确。
     */
    EMAIL_INVALID("VERIFICATION_EMAIL_INVALID", "邮箱格式不正确", 400),

    /**
     * 手机号格式不正确。
     */
    PHONE_INVALID("VERIFICATION_PHONE_INVALID", "手机号格式不正确", 400),

    /**
     * 验证码错误。
     */
    CODE_INVALID("VERIFICATION_CODE_INVALID", "验证码错误", 400),

    /**
     * 验证码已过期。
     */
    CODE_EXPIRED("VERIFICATION_CODE_EXPIRED", "验证码已过期", 400),

    /**
     * 验证码已使用。
     */
    CODE_ALREADY_USED("VERIFICATION_CODE_ALREADY_USED", "验证码已使用", 400),

    /**
     * 验证码目的无效。
     */
    PURPOSE_INVALID("VERIFICATION_PURPOSE_INVALID", "验证码目的无效", 400),

    // ========== 限流错误 ==========

    /**
     * 邮箱限流。
     */
    RATE_LIMIT_EMAIL("VERIFICATION_RATE_LIMIT_EMAIL", "请60秒后再试", 429),

    /**
     * 手机号限流。
     */
    RATE_LIMIT_PHONE("VERIFICATION_RATE_LIMIT_PHONE", "请60秒后再试", 429),

    /**
     * IP限流。
     */
    RATE_LIMIT_IP("VERIFICATION_RATE_LIMIT_IP", "发送次数过多，请稍后再试", 429);

    private final String code;
    private final String message;
    private final int httpStatus;

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

    VerificationCodeError(String code, String message, int httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }
}
