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
    List<String> scopesSupported,
    List<String> responseTypesSupported,
    List<String> grantTypesSupported,
    List<String> subjectTypesSupported,
    List<String> idTokenSigningAlgValuesSupported,
    List<String> tokenEndpointAuthMethodsSupported
) {
}
