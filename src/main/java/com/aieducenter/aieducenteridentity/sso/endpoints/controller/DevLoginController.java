package com.aieducenter.aieducenteridentity.sso.endpoints.controller;

import java.net.URI;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.aieducenter.aieducenteridentity.sso.application.DevLoginAppService;
import com.aieducenter.aieducenteridentity.sso.application.dto.SsoLoginResult;
import com.aieducenter.aieducenteridentity.sso.endpoints.web.SsoCookieService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * dev 一键登端点（issue #16）。仅 {@code identity.sso.dev-login.enabled=true} 时注册——
 * identity-web 登录页缺席时，{@code /authorize} 无 cookie 跳本端点，自动登预置测试账号、发 code。
 * prod 永不开此开关 → 本控制器不注册。
 */
@RestController
@ConditionalOnProperty(prefix = "identity.sso.dev-login", name = "enabled", havingValue = "true")
@Tag(name = "SSO / Dev", description = "dev 一键登（仅 dev/local）")
public class DevLoginController {

    private final DevLoginAppService devLoginService;
    private final SsoCookieService cookieService;

    public DevLoginController(DevLoginAppService devLoginService, SsoCookieService cookieService) {
        this.devLoginService = devLoginService;
        this.cookieService = cookieService;
    }

    @GetMapping("/api/auth/dev-login")
    @Operation(summary = "dev 一键登", description = "登预置测试账号 → 建 SSO 会话 + 种 cookie + 发 code → 302 回 redirect_uri")
    public ResponseEntity<Void> devLogin(
            @RequestParam(value = "client_id", required = false) String clientId,
            @RequestParam(value = "redirect_uri", required = false) String redirectUri,
            @RequestParam(value = "state", required = false) String state,
            @RequestParam(value = "nonce", required = false) String nonce,
            HttpServletResponse response) {
        SsoLoginResult result = devLoginService.loginAsDevAccount(clientId, redirectUri, state, nonce);
        cookieService.setSessionCookie(response, result.sessionId());
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(result.redirectUrl())).build();
    }
}
