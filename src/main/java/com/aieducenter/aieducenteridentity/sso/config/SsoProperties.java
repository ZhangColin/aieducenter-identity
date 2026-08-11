package com.aieducenter.aieducenteridentity.sso.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * SSO 配置属性（{@code identity.sso.*}，ADR-0004 / issue #15）。
 *
 * <p>会话双超时（闲置/绝对）、授权码有效期、SSO cookie、登录页 URL、受 SSO 会话保护的路径、
 * app-registry 远程解析配置（#30 替 stub 消费方）。</p>
 *
 * @since 0.1.0
 */
@Component
@ConfigurationProperties(prefix = "identity.sso")
public class SsoProperties {

    /** SSO 会话闲置超时（秒），Redis key TTL 滑动续期；默认 30 天。 */
    private long sessionIdleSeconds = 2_592_000L;

    /** SSO 会话绝对超时（秒），到期必失效；默认 90 天。 */
    private long sessionAbsoluteSeconds = 7_776_000L;

    /** 授权码有效期（秒），默认 60s（一次性）。 */
    private long codeTtlSeconds = 60L;

    /** SSO cookie 名。 */
    private String cookieName = "sso_session";

    /**
     * SSO cookie 是否标记 Secure（生产 https 必须 true）。
     *
     * <p>注意：{@code .localhost} 仅被 Chrome/Edge 视为 secure context（豁免 Secure-over-http），
     * Safari/Firefox <strong>不</strong>豁免——local 跑 {@code http://*.localhost} 时，Secure cookie
     * 会被 Safari/Firefox 拒存，{@code sso_session} 落不了地、SSO 会话不持久（循环回登录页）。
     * 故 local 需显式 {@code identity.sso.cookie-secure: false}（见 application-local.yml，issue #36）。</p>
     */
    private boolean cookieSecure = true;

    /** 未登录时 /authorize 302 跳转的登录页 URL（identity-web），透传 authorize 参数。 */
    private String loginPageUrl = "https://identity.localhost/login";

    /**
     * 浏览器导航类端点（{@code /authorize}、{@code /logout}）出错时 302 跳转的兜底页 URL（identity-web），
     * 带 {@code ?error&error_description&client_id}（ADR-0006）。
     *
     * <p><b>取自配置、不取自请求参数</b>——防开放重定向（错误页目标不可由调用方控制）。local 指向 identity-web
     * {@code /error}，prod 走 env。</p>
     */
    private String errorPageUrl = "https://identity.localhost/error";

    /** 受 SSO 会话保护的路径（无有效 SSO cookie → 401）。 */
    private List<String> protectedPaths =
        new ArrayList<>(List.of("/api/sso/me", "/api/sso/profile"));

    // ── app-registry 远程解析（#30：消费 app-registry bootstrap 端点替 stub 消费方） ──

    /** app-registry bootstrap 端点配置（base-url；超时走 cartisan.openapi.timeout.*，#31）。 */
    private AppRegistry appRegistry = new AppRegistry();

    public long getSessionIdleSeconds() {
        return sessionIdleSeconds;
    }

    public void setSessionIdleSeconds(long sessionIdleSeconds) {
        this.sessionIdleSeconds = sessionIdleSeconds;
    }

    public long getSessionAbsoluteSeconds() {
        return sessionAbsoluteSeconds;
    }

    public void setSessionAbsoluteSeconds(long sessionAbsoluteSeconds) {
        this.sessionAbsoluteSeconds = sessionAbsoluteSeconds;
    }

    public long getCodeTtlSeconds() {
        return codeTtlSeconds;
    }

    public void setCodeTtlSeconds(long codeTtlSeconds) {
        this.codeTtlSeconds = codeTtlSeconds;
    }

    public String getCookieName() {
        return cookieName;
    }

    public void setCookieName(String cookieName) {
        this.cookieName = cookieName;
    }

    public boolean isCookieSecure() {
        return cookieSecure;
    }

    public void setCookieSecure(boolean cookieSecure) {
        this.cookieSecure = cookieSecure;
    }

    public String getLoginPageUrl() {
        return loginPageUrl;
    }

    public void setLoginPageUrl(String loginPageUrl) {
        this.loginPageUrl = loginPageUrl;
    }

    public String getErrorPageUrl() {
        return errorPageUrl;
    }

    public void setErrorPageUrl(String errorPageUrl) {
        this.errorPageUrl = errorPageUrl;
    }

    public List<String> getProtectedPaths() {
        return protectedPaths;
    }

    public void setProtectedPaths(List<String> protectedPaths) {
        this.protectedPaths = protectedPaths;
    }

    public AppRegistry getAppRegistry() {
        return appRegistry;
    }

    public void setAppRegistry(AppRegistry appRegistry) {
        this.appRegistry = appRegistry;
    }

    /** app-registry bootstrap 端点配置（identity 消费 SsoClient facet 的远程入口）。 */
    public static class AppRegistry {
        /** bootstrap 端点 base-url（如 {@code https://app-registry.aieducenter.com}）；test 指向 WireMock。 */
        private String baseUrl = "http://localhost:18080";

        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    }
}
