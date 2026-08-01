package com.aieducenter.aieducenteridentity.sso.endpoints.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aieducenter.aieducenteridentity.sso.application.OidcDiscoveryAppService;
import com.aieducenter.aieducenteridentity.sso.application.dto.DiscoveryResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * OIDC Discovery Endpoint——{@code GET /.well-known/openid-configuration}（issue #17）。
 *
 * <p>发布 IdP 自发现文档：issuer + authorize/token/userinfo/jwks 端点 + 支持的 scope/grant/算法。
 * 任意技术栈的 OIDC 客户端库凭此自动配置接入。</p>
 */
@RestController
@Tag(name = "SSO / Discovery", description = "OIDC 自发现端点")
public class DiscoveryController {

    private final OidcDiscoveryAppService discoveryService;

    public DiscoveryController(OidcDiscoveryAppService discoveryService) {
        this.discoveryService = discoveryService;
    }

    @GetMapping("/.well-known/openid-configuration")
    @Operation(summary = "OIDC 自发现文档", description = "issuer/端点/scope/jwks_uri 清单")
    public DiscoveryResponse discovery() {
        return discoveryService.discovery();
    }
}
