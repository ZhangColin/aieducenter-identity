package com.aieducenter.aieducenteridentity.sso.endpoints.web;

import java.util.Optional;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import com.aieducenter.aieducenteridentity.sso.config.SsoProperties;

import jakarta.annotation.Nonnull;

/**
 * SSO cookie 读写——浏览器侧 SSO 会话标识（httpOnly + Secure + SameSite=Lax，CONTEXT 部署拓扑）。
 *
 * <p>值是不透明 sessionId（非 token）。登录/注册建会话后 {@link #setSessionCookie} 种 cookie；
 * {@link SsoSessionFilter} 凭 {@link #readSessionId} 认人；登出（#19）用 {@link #clearSessionCookie} 清。</p>
 *
 * @since 0.1.0
 */
@Component
public class SsoCookieService {

    private final SsoProperties properties;

    public SsoCookieService(SsoProperties properties) {
        this.properties = properties;
    }

    /**
     * 从请求 cookie 读 SSO sessionId。
     */
    public Optional<String> readSessionId(@Nonnull HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        for (Cookie cookie : cookies) {
            if (properties.getCookieName().equals(cookie.getName())) {
                String value = cookie.getValue();
                return (value != null && !value.isBlank()) ? Optional.of(value) : Optional.empty();
            }
        }
        return Optional.empty();
    }

    /**
     * 种 SSO cookie（maxAge = 绝对超时；闲置超时由服务端 Redis 滑动续期）。
     */
    public void setSessionCookie(@Nonnull HttpServletResponse response, @Nonnull String sessionId) {
        response.addHeader("Set-Cookie", cookieBuilder(sessionId)
            .maxAge(properties.getSessionAbsoluteSeconds())
            .build()
            .toString());
    }

    /**
     * 清 SSO cookie（登出）。
     */
    public void clearSessionCookie(@Nonnull HttpServletResponse response) {
        response.addHeader("Set-Cookie", cookieBuilder("")
            .maxAge(0)
            .build()
            .toString());
    }

    private ResponseCookie.ResponseCookieBuilder cookieBuilder(String value) {
        return ResponseCookie.from(properties.getCookieName(), value)
            .httpOnly(true)
            .secure(properties.isCookieSecure())
            .sameSite("Lax")
            .path("/");
    }
}
