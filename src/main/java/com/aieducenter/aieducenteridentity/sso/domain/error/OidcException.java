package com.aieducenter.aieducenteridentity.sso.domain.error;

/**
 * OIDC 协议异常——携带 {@link SsoError} + 描述，由 {@code OidcExceptionHandler} 转成
 * {@code {error, error_description}} 响应（区别于内部 {@code ApiResponse} 形态）。
 *
 * @since 0.1.0
 */
public class OidcException extends RuntimeException {

    private final SsoError error;
    private final String description;

    public OidcException(SsoError error, String description) {
        super(error.code() + ": " + description);
        this.error = error;
        this.description = description;
    }

    public SsoError error() {
        return error;
    }

    public String description() {
        return description;
    }
}
