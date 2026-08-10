package com.aieducenter.aieducenteridentity.sso.endpoints.controller;

import java.net.URI;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.aieducenter.aieducenteridentity.sso.application.SsoLogoutAppService;
import com.aieducenter.aieducenteridentity.sso.application.dto.LogoutResult;
import com.aieducenter.aieducenteridentity.sso.domain.error.OidcException;
import com.aieducenter.aieducenteridentity.sso.endpoints.web.OidcErrorPageRedirector;
import com.aieducenter.aieducenteridentity.sso.endpoints.web.SsoCookieService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * OIDC RP-Initiated Logout Endpoint——{@code GET /logout}（CONTEXT 登出 / issue #19）。
 *
 * <p>应用发起：清 SSO 会话 + 清 cookie → 302 回 {@code post_logout_redirect_uri[?state]}（白名单校验通过）；
 * {@code post_logout_redirect_uri} 缺失 → 清会话+cookie 后返回 200（应用未要求回跳）；
 * 校验失败（未登记 / 未知 client）→ 清会话+cookie 后 302 跳 identity-web 兜底页（ADR-0006，提示已登出但未能自动返回）。</p>
 */
@RestController
@Tag(name = "SSO / Logout", description = "OIDC 登出端点")
public class LogoutController {

    private final SsoLogoutAppService logoutService;
    private final SsoCookieService cookieService;
    private final OidcErrorPageRedirector errorPageRedirector;

    public LogoutController(SsoLogoutAppService logoutService, SsoCookieService cookieService,
            OidcErrorPageRedirector errorPageRedirector) {
        this.logoutService = logoutService;
        this.cookieService = cookieService;
        this.errorPageRedirector = errorPageRedirector;
    }

    @GetMapping("/logout")
    @Operation(summary = "OIDC RP-initiated 登出", description = "清 SSO 会话+cookie，302 回 post_logout_redirect_uri（白名单）；缺失则 200，未登记/未知 client 跳兜底页")
    public ResponseEntity<Void> logout(
            @RequestParam(value = "client_id", required = false) String clientId,
            @RequestParam(value = "post_logout_redirect_uri", required = false) String postLogoutRedirectUri,
            @RequestParam(value = "state", required = false) String state,
            HttpServletRequest request,
            HttpServletResponse response) {
        String sessionId = cookieService.readSessionId(request).orElse(null);
        // 先清 cookie：登出优先，无论如何（含校验失败抛 OidcException）都清；会话在 service 内先于校验删除。
        cookieService.clearSessionCookie(response);
        LogoutResult result = logoutService.logout(sessionId, clientId, postLogoutRedirectUri, state);
        if (result.redirectUrl() != null) {
            return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(result.redirectUrl())).build();
        }
        return ResponseEntity.ok().build();
    }

    /**
     * 浏览器类端点错误分流（ADR-0006）：{@code /logout} 校验失败（未登记 post_logout / 未知 client）抛
     * {@link OidcException} 时不返 200 空白，302 跳 identity-web 兜底页。局部 handler 优先于全局
     * {@link com.aieducenter.aieducenteridentity.sso.endpoints.web.OidcExceptionHandler}。
     */
    @ExceptionHandler(OidcException.class)
    public ResponseEntity<Void> onOidcError(OidcException ex, HttpServletRequest request) {
        return errorPageRedirector.redirect(ex, request.getParameter("client_id"));
    }
}
