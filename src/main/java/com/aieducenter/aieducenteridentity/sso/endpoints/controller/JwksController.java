package com.aieducenter.aieducenteridentity.sso.endpoints.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aieducenter.aieducenteridentity.account.domain.token.JwkSetView;
import com.aieducenter.aieducenteridentity.sso.application.OidcDiscoveryAppService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * OIDC JSON Web Key Set Endpoint——{@code GET /jwks}（issue #17）。
 *
 * <p>发布 RS256 公钥集（公钥-only）。对接方 OIDC 客户端库自发现 jwks_uri 后下载本端点，用 kid 选中公钥
 * 本地验 access/id JWT 签名。</p>
 */
@RestController
@Tag(name = "SSO / JWKS", description = "OIDC 公钥集端点")
public class JwksController {

    private final OidcDiscoveryAppService discoveryService;

    public JwksController(OidcDiscoveryAppService discoveryService) {
        this.discoveryService = discoveryService;
    }

    @GetMapping("/jwks")
    @Operation(summary = "OIDC 公钥集", description = "RS256 验签公钥（对接方本地验签用）")
    public JwkSetView jwks() {
        return discoveryService.jwkSet();
    }
}
