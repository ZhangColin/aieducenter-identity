package com.aieducenter.aieducenteridentity.account.endpoints.controller;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aieducenter.aieducenteridentity.account.application.AccountPasswordAppService;
import com.aieducenter.aieducenteridentity.account.application.AccountProfileAppService;
import com.aieducenter.aieducenteridentity.account.application.dto.command.ChangePasswordCommand;
import com.aieducenter.aieducenteridentity.account.application.dto.command.ResetPasswordCommand;
import com.aieducenter.aieducenteridentity.account.application.dto.command.UpdateProfileCommand;
import com.aieducenter.aieducenteridentity.account.application.dto.response.AccountProfileResponse;
import com.cartisan.web.response.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * 账号管理 REST API。
 *
 * <p>公开端点：重置密码（验证码）。受保护端点（SSO 会话过滤器凭 SSO cookie 认人，ADR-0004）：
 * 修改密码 / 查看·编辑 profile / me。认证入口统一在 {@code /api/auth/*} + OIDC 根端点（sso 上下文）。</p>
 */
@RestController
@RequestMapping("/api/account")
@Validated
@Tag(name = "Account", description = "账号管理")
public class AccountController {

    private final AccountPasswordAppService passwordAppService;
    private final AccountProfileAppService profileAppService;

    public AccountController(AccountPasswordAppService passwordAppService,
            AccountProfileAppService profileAppService) {
        this.passwordAppService = passwordAppService;
        this.profileAppService = profileAppService;
    }

    @PostMapping("/reset-password")
    @Operation(summary = "重置密码", description = "经手机/邮箱验证码重置密码，重置后踢出所有会话")
    public ApiResponse<Void> resetPassword(@Valid @RequestBody ResetPasswordCommand command) {
        passwordAppService.resetPassword(command);
        return ApiResponse.ok();
    }

    @PostMapping("/change-password")
    @Operation(summary = "修改密码", description = "验证旧密码后修改，修改后踢出所有会话（凭 SSO cookie）")
    public ApiResponse<Void> changePassword(@Valid @RequestBody ChangePasswordCommand command) {
        passwordAppService.changePassword(command);
        return ApiResponse.ok();
    }

    @GetMapping("/me")
    @Operation(summary = "当前登录用户", description = "返回当前登录用户的 profile（凭 SSO cookie，issue #15）")
    public ApiResponse<AccountProfileResponse> getCurrentUser() {
        return ApiResponse.ok(profileAppService.getCurrentProfile());
    }

    @GetMapping("/profile")
    @Operation(summary = "查看个人资料", description = "返回当前登录用户的 profile（凭 SSO cookie）")
    public ApiResponse<AccountProfileResponse> getProfile() {
        return ApiResponse.ok(profileAppService.getCurrentProfile());
    }

    @PutMapping("/profile")
    @Operation(summary = "编辑个人资料", description = "更新当前登录用户的昵称/头像（仅非空字段，凭 SSO cookie）")
    public ApiResponse<Void> updateProfile(@Valid @RequestBody UpdateProfileCommand command) {
        profileAppService.updateCurrentProfile(command);
        return ApiResponse.ok();
    }
}
