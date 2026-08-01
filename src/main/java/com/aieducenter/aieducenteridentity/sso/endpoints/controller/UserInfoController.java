package com.aieducenter.aieducenteridentity.sso.endpoints.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.aieducenter.aieducenteridentity.sso.application.SsoUserInfoAppService;
import com.aieducenter.aieducenteridentity.sso.application.dto.UserInfoResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * OIDC UserInfo Endpoint——{@code GET /userinfo}（issue #17）。
 *
 * <p>消费方 BFF 持 access JWT 调用：{@code Authorization: Bearer <access_token>} → 返回 sub + 按授权 scope 的
 * profile/email/phone 资料。无效/过期令牌走 {@code OidcExceptionHandler}（401 {@code invalid_token}）。</p>
 */
@RestController
@Tag(name = "SSO / UserInfo", description = "OIDC 用户资料端点")
public class UserInfoController {

    private final SsoUserInfoAppService userInfoService;

    public UserInfoController(SsoUserInfoAppService userInfoService) {
        this.userInfoService = userInfoService;
    }

    @GetMapping("/userinfo")
    @Operation(summary = "OIDC 用户资料端点", description = "Bearer access token 换用户资料（按 scope）")
    public UserInfoResponse userInfo(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return userInfoService.userInfo(authorization);
    }
}
