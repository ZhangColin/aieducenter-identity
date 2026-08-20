package com.aieducenter.aieducenteridentity.sso.config;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.aieducenter.aieducenteridentity.sso.infrastructure.token.JwtTokenProperties;

/**
 * {@link EnvironmentUrlGuard} 单元测试（issue #74 AC）。
 *
 * <p>issuer / 登录页 / 错误页三者任一为空（null 或空白）→ 启动 fail-fast 且消息指明具体键名；
 * 全配 → 放行。</p>
 */
class EnvironmentUrlGuardTest {

    private static JwtTokenProperties tokenWithIssuer(String issuer) {
        JwtTokenProperties token = new JwtTokenProperties();
        token.setIssuer(issuer);
        return token;
    }

    private static SsoProperties ssoWithPages(String loginPageUrl, String errorPageUrl) {
        SsoProperties sso = new SsoProperties();
        sso.setLoginPageUrl(loginPageUrl);
        sso.setErrorPageUrl(errorPageUrl);
        return sso;
    }

    @Test
    void given_all_urls_present_when_check_then_pass() {
        // Given
        EnvironmentUrlGuard guard = new EnvironmentUrlGuard(
            tokenWithIssuer("http://identity.localhost:10001"),
            ssoWithPages("http://identity.localhost:10002/login", "http://identity.localhost:10002/error"));

        // When & Then — 不抛
        assertThatCode(guard::checkEnvironmentUrlsPresent).doesNotThrowAnyException();
    }

    @Test
    void given_issuer_missing_when_check_then_throw_with_key_name() {
        // Given
        EnvironmentUrlGuard guard = new EnvironmentUrlGuard(
            tokenWithIssuer(null),
            ssoWithPages("http://identity.localhost:10002/login", "http://identity.localhost:10002/error"));

        // When & Then
        assertThatThrownBy(guard::checkEnvironmentUrlsPresent)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("identity.token.jwt.issuer");
    }

    @Test
    void given_login_page_missing_when_check_then_throw_with_key_name() {
        // Given — 空串与 null 同罪
        EnvironmentUrlGuard guard = new EnvironmentUrlGuard(
            tokenWithIssuer("http://identity.localhost:10001"),
            ssoWithPages("", "http://identity.localhost:10002/error"));

        // When & Then
        assertThatThrownBy(guard::checkEnvironmentUrlsPresent)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("identity.sso.login-page-url");
    }

    @Test
    void given_error_page_missing_when_check_then_throw_with_key_name() {
        // Given — 空白串与 null 同罪
        EnvironmentUrlGuard guard = new EnvironmentUrlGuard(
            tokenWithIssuer("http://identity.localhost:10001"),
            ssoWithPages("http://identity.localhost:10002/login", " "));

        // When & Then
        assertThatThrownBy(guard::checkEnvironmentUrlsPresent)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("identity.sso.error-page-url");
    }

    @Test
    void given_all_missing_when_check_then_throw_lists_every_key() {
        // Given
        EnvironmentUrlGuard guard = new EnvironmentUrlGuard(tokenWithIssuer(null), ssoWithPages(null, null));

        // When & Then — 一次报全，不挤牙膏
        assertThatThrownBy(guard::checkEnvironmentUrlsPresent)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("identity.token.jwt.issuer")
            .hasMessageContaining("identity.sso.login-page-url")
            .hasMessageContaining("identity.sso.error-page-url");
    }

    @Test
    void given_unresolved_placeholder_when_check_then_throw_with_key_name() {
        // Given — prod 裸 ${ENV} 在 env 缺失时 binder 不炸、保留字面量穿透（non-strict），guard 须识破
        EnvironmentUrlGuard guard = new EnvironmentUrlGuard(
            tokenWithIssuer("${IDENTITY_TOKEN_ISSUER}"),
            ssoWithPages("${IDENTITY_SSO_LOGIN_PAGE_URL}", "${IDENTITY_SSO_ERROR_PAGE_URL}"));

        // When & Then
        assertThatThrownBy(guard::checkEnvironmentUrlsPresent)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("identity.token.jwt.issuer")
            .hasMessageContaining("identity.sso.login-page-url")
            .hasMessageContaining("identity.sso.error-page-url");
    }
}
