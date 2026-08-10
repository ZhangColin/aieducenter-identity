package com.aieducenter.aieducenteridentity.sso.application;

import java.util.List;

import org.springframework.stereotype.Service;

import com.aieducenter.aieducenteridentity.account.domain.token.JwkSetView;
import com.aieducenter.aieducenteridentity.account.domain.token.SigningKeyCatalog;
import com.aieducenter.aieducenteridentity.sso.application.dto.DiscoveryResponse;

/**
 * OIDC 自发现 / 元数据应用服务——{@code /jwks} 与 {@code /.well-known/openid-configuration} 共用（issue #17）。
 *
 * <p>{@code /jwks} 发布签名公钥集（{@link SigningKeyCatalog}）；{@code /discovery} 发布 issuer/端点/scope 清单。
 * 把 OIDC 协议元数据的装配集中在此，控制器保持薄。</p>
 *
 * @since 0.1.0
 */
@Service
public class OidcDiscoveryAppService {

    private static final List<String> SCOPES_SUPPORTED = List.of("openid", "profile", "email", "phone");
    private static final List<String> RESPONSE_TYPES_SUPPORTED = List.of("code");
    private static final List<String> GRANT_TYPES_SUPPORTED = List.of("authorization_code", "refresh_token");
    private static final List<String> SUBJECT_TYPES_SUPPORTED = List.of("public");
    private static final List<String> ID_TOKEN_ALGS_SUPPORTED = List.of("RS256");
    private static final List<String> TOKEN_ENDPOINT_AUTH_METHODS = List.of("client_secret_post");

    private final SigningKeyCatalog signingKeyCatalog;

    public OidcDiscoveryAppService(SigningKeyCatalog signingKeyCatalog) {
        this.signingKeyCatalog = signingKeyCatalog;
    }

    /**
     * /jwks——当前签名公钥集（公钥-only，对接方本地验签用）。
     */
    public JwkSetView jwkSet() {
        return signingKeyCatalog.publicJwkSet();
    }

    /**
     * /.well-known/openid-configuration——issuer + 端点 + 支持的 scope/grant/算法清单。
     *
     * <p>端点 = {@code issuer + 路径}；{@code issuer} 与 token 的 {@code iss} 同源（均来自 {@link SigningKeyCatalog#issuer()}）。
     * logout 能力（issue #41）：{@code end_session_endpoint} 指向 {@code /logout}；identity 不支持
     * front/back-channel SLO、支持 post_logout_redirect。</p>
     */
    public DiscoveryResponse discovery() {
        String issuer = signingKeyCatalog.issuer();
        return new DiscoveryResponse(
            issuer,
            issuer + "/authorize",
            issuer + "/token",
            issuer + "/userinfo",
            issuer + "/jwks",
            issuer + "/logout",
            SCOPES_SUPPORTED,
            RESPONSE_TYPES_SUPPORTED,
            GRANT_TYPES_SUPPORTED,
            SUBJECT_TYPES_SUPPORTED,
            ID_TOKEN_ALGS_SUPPORTED,
            TOKEN_ENDPOINT_AUTH_METHODS,
            false,
            false,
            true);
    }
}

