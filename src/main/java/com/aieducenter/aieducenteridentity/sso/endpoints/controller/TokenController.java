package com.aieducenter.aieducenteridentity.sso.endpoints.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.aieducenter.aieducenteridentity.sso.application.SsoTokenAppService;
import com.aieducenter.aieducenteridentity.sso.application.dto.TokenRequest;
import com.aieducenter.aieducenteridentity.sso.application.dto.TokenResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * OIDC Token Endpoint——{@code POST /token}（CONTEXT「token 归 /token」/ issue #15）。
 *
 * <p>机机直连（消费方 BFF 带 client_secret）。表单参数：{@code grant_type/code/redirect_uri/
 * client_id/client_secret/refresh_token}。返回扁平 OIDC token JSON；错误走 {@code OidcExceptionHandler}
 * （{@code {error, error_description}} + 400/401）。</p>
 */
@RestController
@Tag(name = "SSO / Token", description = "OIDC 令牌端点")
public class TokenController {

    private final SsoTokenAppService tokenService;

    public TokenController(SsoTokenAppService tokenService) {
        this.tokenService = tokenService;
    }

    @PostMapping(value = "/token", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    @Operation(summary = "OIDC 令牌端点", description = "code 换 access/id/refresh；refresh 轮换")
    public TokenResponse token(
            @RequestParam("grant_type") String grantType,
            @RequestParam(value = "code", required = false) String code,
            @RequestParam(value = "redirect_uri", required = false) String redirectUri,
            @RequestParam(value = "client_id", required = false) String clientId,
            @RequestParam(value = "client_secret", required = false) String clientSecret,
            @RequestParam(value = "refresh_token", required = false) String refreshToken) {
        return tokenService.token(
            new TokenRequest(grantType, code, redirectUri, clientId, clientSecret, refreshToken));
    }
}
