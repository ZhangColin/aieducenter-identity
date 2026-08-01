package com.aieducenter.demobff.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * demo BFF 的 SSO/OIDC 消费方配置（{@code sso.*}）。
 */
@Component
@ConfigurationProperties(prefix = "sso")
public class SsoProperties {

    /** identity IdP 地址（local：http://identity.localhost:10001）。 */
    private String issuer;
    /** 消费方 client_id（identity stub：demo-client）。 */
    private String clientId;
    /** 消费方 client_secret（BFF 服务端用，浏览器不接触）。 */
    private String clientSecret;
    /** 回调地址（浏览器入口，经 Next 代理到本 BFF）。 */
    private String redirectUri;
    /** 授权范围。 */
    private String scope = "openid profile";
    /** 前端地址（登录/登出后回跳）。 */
    private String appBaseUrl;

    public String getIssuer() { return issuer; }
    public void setIssuer(String issuer) { this.issuer = issuer; }
    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }
    public String getClientSecret() { return clientSecret; }
    public void setClientSecret(String clientSecret) { this.clientSecret = clientSecret; }
    public String getRedirectUri() { return redirectUri; }
    public void setRedirectUri(String redirectUri) { this.redirectUri = redirectUri; }
    public String getScope() { return scope; }
    public void setScope(String scope) { this.scope = scope; }
    public String getAppBaseUrl() { return appBaseUrl; }
    public void setAppBaseUrl(String appBaseUrl) { this.appBaseUrl = appBaseUrl; }
}
