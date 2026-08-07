package com.aieducenter.demobff.sso;

/**
 * 用 code 换 token 失败（identity {@code /token} 非 2xx，或连接/超时/响应解析失败）。
 *
 * <p>把被吞的失败细节带出来供 BFF 记日志排查：{@link #httpStatus}（连接/解析失败为 {@code null}）+
 * {@link #responseBody}（identity {@code /token} 的 {@code {error, error_description}} 协议错误体）。
 * issue #35——此前 {@code /token} 的具体拒绝原因被笼统吞成 {@code exchange_failed}。</p>
 */
public class TokenExchangeException extends RuntimeException {

    private final Integer httpStatus;
    private final String responseBody;

    public TokenExchangeException(String message, Integer httpStatus, String responseBody, Throwable cause) {
        super(message, cause);
        this.httpStatus = httpStatus;
        this.responseBody = responseBody;
    }

    /** identity {@code /token} 的 HTTP 状态码；连接/超时/响应解析失败等无状态码时为 {@code null}。 */
    public Integer getHttpStatus() {
        return httpStatus;
    }

    /** identity {@code /token} 的响应体（通常为 {@code {error, error_description}}）；无响应体时为 {@code null}。 */
    public String getResponseBody() {
        return responseBody;
    }
}
