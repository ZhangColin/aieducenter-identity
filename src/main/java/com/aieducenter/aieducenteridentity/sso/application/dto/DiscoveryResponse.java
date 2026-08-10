package com.aieducenter.aieducenteridentity.sso.application.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/**
 * {@code /.well-known/openid-configuration} 响应——OIDC 自发现文档（issue #17）。
 *
 * <p>字段按 OIDC Discovery（RFC 8414 + OIDC Discovery）命名；{@link JsonNaming} 把 camelCase 映射为
 * snake_case（{@code authorizationEndpoint}→{@code authorization_endpoint}）。{@code issuer} 必须与
 * token 的 {@code iss} 一致——消费方据此定位所有端点 + 本地校验 issuer claim。</p>
 *
 * <p>logout 能力字段（issue #41）：{@code endSessionEndpoint} 指向 RP-Initiated Logout 端点
 * {@code /logout}；{@code frontchannelLogoutSupported}/{@code backchannelLogoutSupported} 明确 identity
 * 不支持 front/back-channel SLO；{@code postLogoutRedirectUrisSupported} 声明支持登出回跳。三个能力
 * 标志为基本类型（永不 null），在 {@code NON_NULL} 下 {@code false} 照常序列化。</p>
 *
 * @since 0.1.0
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DiscoveryResponse(
    String issuer,
    String authorizationEndpoint,
    String tokenEndpoint,
    String userinfoEndpoint,
    String jwksUri,
    String endSessionEndpoint,
    List<String> scopesSupported,
    List<String> responseTypesSupported,
    List<String> grantTypesSupported,
    List<String> subjectTypesSupported,
    List<String> idTokenSigningAlgValuesSupported,
    List<String> tokenEndpointAuthMethodsSupported,
    boolean frontchannelLogoutSupported,
    boolean backchannelLogoutSupported,
    boolean postLogoutRedirectUrisSupported
) {
}
