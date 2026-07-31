package com.aieducenter.aieducenteridentity.sso.endpoints.controller;

import java.net.URI;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.aieducenter.aieducenteridentity.sso.application.SsoAuthorizeAppService;
import com.aieducenter.aieducenteridentity.sso.application.dto.AuthorizeRequest;
import com.aieducenter.aieducenteridentity.sso.endpoints.web.SsoCookieService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * OIDC Authorization Endpoint——{@code GET /authorize}（CONTEXT 登录契约 / issue #15）。
 *
 * <p>消费方发起：验 client/redirect_uri → 看 SSO cookie，有则发 code 302 回 redirect_uri（二次免登），
 * 无则 302 到登录页透传 authorize 参数。client/redirect_uri 无效 → 不重定向、返回 OIDC 错误。</p>
 */
@RestController
@Tag(name = "SSO / Authorize", description = "OIDC 授权端点")
public class AuthorizeController {

    private final SsoAuthorizeAppService authorizeService;
    private final SsoCookieService cookieService;

    public AuthorizeController(SsoAuthorizeAppService authorizeService, SsoCookieService cookieService) {
        this.authorizeService = authorizeService;
        this.cookieService = cookieService;
    }

    @GetMapping("/authorize")
    @Operation(summary = "OIDC 授权端点", description = "有 SSO 会话发 code 302；无则 302 登录页")
    public ResponseEntity<Void> authorize(
            @RequestParam(value = "client_id", required = false) String clientId,
            @RequestParam(value = "redirect_uri", required = false) String redirectUri,
            @RequestParam(value = "response_type", required = false) String responseType,
            @RequestParam(value = "state", required = false) String state,
            @RequestParam(value = "nonce", required = false) String nonce,
            @RequestParam(value = "scope", required = false) String scope,
            HttpServletRequest request) {
        String sessionId = cookieService.readSessionId(request).orElse(null);
        String location = authorizeService.handleAuthorize(
            new AuthorizeRequest(clientId, redirectUri, state, nonce, scope), sessionId);
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(location)).build();
    }
}
