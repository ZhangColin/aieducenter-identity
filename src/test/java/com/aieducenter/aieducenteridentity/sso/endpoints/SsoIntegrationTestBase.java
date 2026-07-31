package com.aieducenter.aieducenteridentity.sso.endpoints;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

import com.aieducenter.aieducenteridentity.sso.config.SsoProperties;
import com.aieducenter.aieducenteridentity.sso.domain.session.SsoSession;
import com.aieducenter.aieducenteridentity.sso.domain.session.SsoSessionRepository;
import com.aieducenter.aieducenteridentity.test.IdentityIntegrationTestBase;
import com.jayway.jsonpath.JsonPath;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import jakarta.servlet.http.Cookie;

/**
 * SSO HTTP 黑盒集成测试基类（issue #15）。
 *
 * <p>背靠 {@link IdentityIntegrationTestBase}（Testcontainers 真 PG+Redis），提供 stub 消费方常量、
 * 建 SSO 会话、构造 SSO cookie、从 302 Location 抽 query 参数等共用助手。</p>
 */
public abstract class SsoIntegrationTestBase extends IdentityIntegrationTestBase {

    /** stub 预置消费方（{@code SsoProperties} 默认值）。 */
    protected static final String CLIENT_ID = "demo-client";
    protected static final String CLIENT_SECRET = "demo-secret-please-change";
    protected static final String REDIRECT_URI = "https://demo.localhost/auth/callback";

    @Autowired
    protected SsoSessionRepository sessionRepository;

    @Autowired
    protected SsoProperties ssoProperties;

    /** 身份域 RSA 公钥（验签 access/id JWT）。 */
    @Autowired
    protected RSAKey identityRsaKey;

    /** 用签名公钥本地验签 JWT，返回声明集；签名不匹配抛断言错误。 */
    protected JWTClaimsSet verifyJwtWithPublicKey(String compactJwt) throws Exception {
        SignedJWT jwt = SignedJWT.parse(compactJwt);
        if (!jwt.verify(new RSASSAVerifier(identityRsaKey.toRSAPublicKey()))) {
            throw new AssertionError("JWT 签名验证失败（公钥不匹配/被篡改）");
        }
        return jwt.getJWTClaimsSet();
    }

    /** 建一个 SSO 会话，返回 sessionId（可装入 cookie）。 */
    protected SsoSession createSsoSession(Long userId, String displayName) {
        return sessionRepository.create(userId, displayName);
    }

    /** 构造 SSO cookie。 */
    protected Cookie ssoCookie(String sessionId) {
        Cookie cookie = new Cookie(ssoProperties.getCookieName(), sessionId);
        cookie.setSecure(true);
        cookie.setHttpOnly(true);
        return cookie;
    }

    /** 从 Set-Cookie 头抽指定 cookie 的值。 */
    protected static String extractCookieValue(MvcResult result, String cookieName) {
        String setCookie = result.getResponse().getHeader("Set-Cookie");
        java.util.Objects.requireNonNull(setCookie, "无 Set-Cookie 头");
        String prefix = cookieName + "=";
        int start = setCookie.indexOf(prefix) + prefix.length();
        int end = setCookie.indexOf(';', start);
        return setCookie.substring(start, end > 0 ? end : setCookie.length());
    }

    /** 从 302 Location 头抽指定 query 参数值。 */
    protected static String queryParam(MvcResult result, String name) {
        String location = result.getResponse().getHeader("Location");
        return queryParam(location, name);
    }

    /** 从 URL 字符串抽指定 query 参数值。 */
    protected static String queryParam(String url, String name) {
        String query = url.substring(url.indexOf('?') + 1);
        for (String kv : query.split("&")) {
            String[] parts = kv.split("=", 2);
            if (parts[0].equals(name)) {
                return java.net.URLDecoder.decode(parts[1], java.nio.charset.StandardCharsets.UTF_8);
            }
        }
        throw new IllegalArgumentException("参数不存在: " + name + " in " + url);
    }

    /** 从 JSON 响应体读 OIDC error 字段。 */
    protected static String oidcError(MvcResult result) throws java.io.UnsupportedEncodingException {
        return JsonPath.read(result.getResponse().getContentAsString(), "$.error");
    }
}
