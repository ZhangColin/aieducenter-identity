package com.aieducenter.aieducenteridentity.verification.domain.error;

import com.cartisan.core.exception.CodeMessage;

/**
 * 图形验证码相关错误码。
 *
 * @since 0.1.0
 */
public enum CaptchaError implements CodeMessage {
    /**
     * 图形验证码错误。
     */
    CAPTCHA_INVALID("CAPTCHA_INVALID", "图形验证码错误", 400),

    /**
     * 图形验证码已过期。
     */
    CAPTCHA_EXPIRED("CAPTCHA_EXPIRED", "图形验证码已过期", 400);

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

    CaptchaError(String code, String message, int httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }
}
