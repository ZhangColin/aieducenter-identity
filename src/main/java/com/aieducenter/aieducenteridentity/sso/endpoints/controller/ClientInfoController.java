package com.aieducenter.aieducenteridentity.sso.endpoints.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.aieducenter.aieducenteridentity.sso.application.SsoClientInfoAppService;
import com.aieducenter.aieducenteridentity.sso.application.dto.ClientInfoResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * client-info 公开查询端点——{@code GET /api/sso/client-info?client_id=}（issue #24、#55）。
 *
 * <p>登录页从 /authorize 透传只拿到 client_id，凭本端点查应用名显示「登录到 XXX 应用」。
 * <b>公开端点</b>（未登录可访问，不在 identity.sso.protected-paths 内，SsoSessionFilter 放行）；
 * 只回最小字段（clientId + clientName），不泄露 client_secret/redirectUris/scopes。
 * client_id 缺失/无效/停用 → 400 unauthorized_client（与 /authorize 重定向前错同一形态，不重定向）。</p>
 */
@RestController
@RequestMapping("/api/sso")
@Tag(name = "SSO / Auth", description = "认证入口（建会话发 code）")
public class ClientInfoController {

    private final SsoClientInfoAppService clientInfoService;

    public ClientInfoController(SsoClientInfoAppService clientInfoService) {
        this.clientInfoService = clientInfoService;
    }

    @GetMapping("/client-info")
    @Operation(summary = "消费方展示信息（公开）", description = "登录页「登录到 XXX 应用」用；只回 clientId + clientName")
    public ClientInfoResponse clientInfo(@RequestParam(value = "client_id", required = false) String clientId) {
        return clientInfoService.clientInfo(clientId);
    }
}
