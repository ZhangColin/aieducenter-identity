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

    /** SSO cookie 是否标记 Secure（生产必须 true；本地 .localhost 为 secure context 亦可 true）。 */
    private boolean cookieSecure = true;

    /** 未登录时 /authorize 302 跳转的登录页 URL（identity-web），透传 authorize 参数。 */
    private String loginPageUrl = "https://identity.localhost/login";

    /** 受 SSO 会话保护的路径（无有效 SSO cookie → 401）。 */
    private List<String> protectedPaths = new ArrayList<>(List.of("/api/account/me", "/api/account/profile"));

    // ── app-registry 远程解析（#30：消费 app-registry bootstrap 端点替 stub 消费方） ──

    /** app-registry bootstrap 端点配置（base-url + 超时）。 */
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

        /** 连接超时（秒）。 */
        private int connectTimeoutSeconds = 3;

        /** 读超时（秒）。 */
        private int readTimeoutSeconds = 5;

        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public int getConnectTimeoutSeconds() { return connectTimeoutSeconds; }
        public void setConnectTimeoutSeconds(int connectTimeoutSeconds) { this.connectTimeoutSeconds = connectTimeoutSeconds; }
        public int getReadTimeoutSeconds() { return readTimeoutSeconds; }
        public void setReadTimeoutSeconds(int readTimeoutSeconds) { this.readTimeoutSeconds = readTimeoutSeconds; }
    }

    // ── dev 一键登（#16：identity-web 缺席时兜登录页；仅 dev/local，prod 不开此开关） ——

    /** dev 一键登配置（开关 + 预置测试账号）。 */
    private DevLogin devLogin = new DevLogin();

    public DevLogin getDevLogin() {
        return devLogin;
    }

    public void setDevLogin(DevLogin devLogin) {
        this.devLogin = devLogin;
    }

    /** dev 一键登配置项。开关默认 false；开启后 {@code /authorize} 无 cookie 跳本端点、自动登预置账号。 */
    public static class DevLogin {
        /** 是否启用 dev 一键登（prod 必须为 false）。 */
        private boolean enabled = false;
        /** 预置测试账号邮箱（dev-login 按此定位账号）。 */
        private String accountEmail = "demo@aieducenter.com";
        /** 预置测试账号明文密码（seeder 加密入库；也供真实登录页联调用）。 */
        private String accountPassword = "demo12345";
        /** 预置测试账号昵称。 */
        private String accountNickname = "Demo 用户";

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getAccountEmail() { return accountEmail; }
        public void setAccountEmail(String accountEmail) { this.accountEmail = accountEmail; }
        public String getAccountPassword() { return accountPassword; }
        public void setAccountPassword(String accountPassword) { this.accountPassword = accountPassword; }
        public String getAccountNickname() { return accountNickname; }
        public void setAccountNickname(String accountNickname) { this.accountNickname = accountNickname; }
    }
}
