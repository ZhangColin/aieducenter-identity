package com.aieducenter.aieducenteridentity.sso.endpoints.controller;

import java.net.URI;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aieducenter.aieducenteridentity.sso.application.SsoLoginAppService;
import com.aieducenter.aieducenteridentity.sso.application.SsoRegisterAppService;
import com.aieducenter.aieducenteridentity.sso.application.dto.LoginByPasswordSsoCommand;
import com.aieducenter.aieducenteridentity.sso.application.dto.RegisterByPasswordSsoCommand;
import com.aieducenter.aieducenteridentity.sso.application.dto.SsoLoginResult;
import com.aieducenter.aieducenteridentity.sso.endpoints.web.SsoCookieService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 认证入口（{@code /api/auth/*}）——建 SSO 会话 + 发 code（CONTEXT 接口命名空间 / issue #15、#18）。
 *
 * <p>密码登录：验凭据 → 建 SSO 会话 + 种 cookie → 发 code → 302 回 {@code redirect_uri?code&state}。
 * 密码注册：唯一性校验 → 建号 → 注册即登录（同一后半段：建会话 + 种 cookie + 发 code + 302）。
 * 登录/注册失败抛领域异常，留登录页显示（不回业务应用）。</p>
 */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "SSO / Auth", description = "认证入口（建会话发 code）")
public class AuthController {

    private final SsoLoginAppService loginService;
    private final SsoRegisterAppService registerService;
    private final SsoCookieService cookieService;

    public AuthController(SsoLoginAppService loginService, SsoRegisterAppService registerService,
            SsoCookieService cookieService) {
        this.loginService = loginService;
        this.registerService = registerService;
        this.cookieService = cookieService;
    }

    @PostMapping("/login")
    @Operation(summary = "密码登录", description = "验凭据 → 建 SSO 会话 + 种 cookie + 发 code + 302 回 redirect_uri")
    public ResponseEntity<Void> loginByPassword(@Valid @RequestBody LoginByPasswordSsoCommand command,
            HttpServletResponse response) {
        SsoLoginResult result = loginService.loginByPassword(command);
        cookieService.setSessionCookie(response, result.sessionId());
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(result.redirectUrl())).build();
    }

    @PostMapping("/register")
    @Operation(summary = "密码注册", description = "唯一性校验 → 建号 → 注册即登录（建 SSO 会话 + 种 cookie + 发 code + 302 回 redirect_uri）")
    public ResponseEntity<Void> registerByPassword(@Valid @RequestBody RegisterByPasswordSsoCommand command,
            HttpServletResponse response) {
        SsoLoginResult result = registerService.registerByPassword(command);
        cookieService.setSessionCookie(response, result.sessionId());
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(result.redirectUrl())).build();
    }
}
