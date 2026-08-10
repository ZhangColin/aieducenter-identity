package com.aieducenter.aieducenteridentity.sso.domain.error;

import com.cartisan.core.exception.CodeMessage;

/**
 * OIDC 标准错误码（RFC 6749 §5.2 / RFC 6750 / OIDC Core）——{@code /authorize}、{@code /token}、
 * {@code /userinfo} 错误响应用。
 *
 * <p>实现 {@link CodeMessage}（架构守护：domain 枚举须实现 BaseEnum 或 CodeMessage；OIDC 错误码为字符串、
 * 走 {@code {error, error_description}} 形态，故选 CodeMessage 而非 Integer 的 BaseEnum）。
 * {@code /token} 错 → 400/401 + {@code {error, error_description}}；{@code /userinfo} 凭证无效 → 401
 * {@code invalid_token}（RFC 6750）；{@code /authorize} 重定向前错（client/redirect_uri 无效）→ 不重定向、
 * 返回错误（防开放重定向）。</p>
 *
 * @since 0.1.0
 */
public enum SsoError implements CodeMessage {

    /** 请求缺参/非法（如 redirect_uri 不在白名单、缺 code/refresh_token）。 */
    INVALID_REQUEST("invalid_request", 400, "请求无效"),
    /** client 未授权该操作（如 client_id 不存在/停用、未授权该 grant_type）。 */
    UNAUTHORIZED_CLIENT("unauthorized_client", 400, "client 未授权"),
    /** 资源所有者拒绝授权。 */
    ACCESS_DENIED("access_denied", 400, "拒绝授权"),
    /** client 认证失败（client_secret 错/缺）——/token 用，401。 */
    INVALID_CLIENT("invalid_client", 401, "client 认证失败"),
    /** 授权码/refresh_token 非法、已用、过期、或不属于该 client。 */
    INVALID_GRANT("invalid_grant", 400, "授权凭证无效或已过期"),
    /** 不支持的 grant_type。 */
    UNSUPPORTED_GRANT_TYPE("unsupported_grant_type", 400, "不支持的 grant_type"),
    /** access token 无效/过期/验签失败——/userinfo 用，401（RFC 6750）。 */
    INVALID_TOKEN("invalid_token", 401, "访问令牌无效或已过期"),
    /** 授权服务器暂时不可用（如依赖的 app-registry 抖动且无缓存兜底）——RFC 6749 §4.1.2.1 / §5.2，503。 */
    TEMPORARILY_UNAVAILABLE("temporarily_unavailable", 503, "服务暂时不可用");

    private final String code;
    private final int httpStatus;
    private final String message;

    SsoError(String code, int httpStatus, String message) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.message = message;
    }

    /** OIDC error 字段值（如 {@code invalid_grant}）。 */
    @Override
    public String code() {
        return code;
    }

    /** 对应 HTTP 状态码。 */
    @Override
    public int httpStatus() {
        return httpStatus;
    }

    @Override
    public String message() {
        return message;
    }
}
