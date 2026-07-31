package com.aieducenter.aieducenteridentity.account.endpoints.controller;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aieducenter.aieducenteridentity.account.application.AccountLoginAppService;
import com.aieducenter.aieducenteridentity.account.application.AccountPasswordAppService;
import com.aieducenter.aieducenteridentity.account.application.AccountProfileAppService;
import com.aieducenter.aieducenteridentity.account.application.AccountRegistrationAppService;
import com.aieducenter.aieducenteridentity.account.application.AccountTokenAppService;
import com.aieducenter.aieducenteridentity.account.application.dto.command.ChangePasswordCommand;
import com.aieducenter.aieducenteridentity.account.application.dto.command.LoginByPasswordCommand;
import com.aieducenter.aieducenteridentity.account.application.dto.command.LoginBySmsCommand;
import com.aieducenter.aieducenteridentity.account.application.dto.command.RefreshTokenCommand;
import com.aieducenter.aieducenteridentity.account.application.dto.command.RegisterCommand;
import com.aieducenter.aieducenteridentity.account.application.dto.command.ResetPasswordCommand;
import com.aieducenter.aieducenteridentity.account.application.dto.command.UpdateProfileCommand;
import com.aieducenter.aieducenteridentity.account.application.dto.response.AccountProfileResponse;
import com.aieducenter.aieducenteridentity.account.application.dto.response.LoginResponse;
import com.cartisan.security.annotation.RequireAuth;
import com.cartisan.web.response.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * 账号管理 REST API。
 *
 * <p>公开端点（无需登录）：注册 / 密码登录 / 短信码登录 / 重置密码 / 刷新 token。
 * 受保护端点（{@link RequireAuth}，bug#2）：登出 / 修改密码 / 查看·编辑 profile。</p>
 */
@RestController
@RequestMapping("/api/account")
@Validated
@Tag(name = "Account", description = "账号管理")
public class AccountController {

    private final AccountRegistrationAppService registrationAppService;
    private final AccountLoginAppService loginAppService;
    private final AccountPasswordAppService passwordAppService;
    private final AccountProfileAppService profileAppService;
    private final AccountTokenAppService tokenAppService;

    public AccountController(AccountRegistrationAppService registrationAppService,
            AccountLoginAppService loginAppService,
            AccountPasswordAppService passwordAppService,
            AccountProfileAppService profileAppService,
            AccountTokenAppService tokenAppService) {
        this.registrationAppService = registrationAppService;
        this.loginAppService = loginAppService;
        this.passwordAppService = passwordAppService;
        this.profileAppService = profileAppService;
        this.tokenAppService = tokenAppService;
    }

    @PostMapping("/register")
    @Operation(summary = "注册", description = "邮箱+验证码 或 手机号+验证码（验过才建号），建号后即登录")
    public ApiResponse<LoginResponse> register(@Valid @RequestBody RegisterCommand command) {
        return ApiResponse.ok(registrationAppService.register(command));
    }

    @PostMapping("/login")
    @Operation(summary = "密码登录", description = "邮箱/手机号 + 密码 + 图形验证码")
    public ApiResponse<LoginResponse> loginByPassword(@Valid @RequestBody LoginByPasswordCommand command) {
        return ApiResponse.ok(loginAppService.loginByPassword(command));
    }

    @PostMapping("/login/sms")
    @Operation(summary = "短信验证码登录", description = "手机号 + 短信验证码")
    public ApiResponse<LoginResponse> loginBySms(@Valid @RequestBody LoginBySmsCommand command) {
        return ApiResponse.ok(loginAppService.loginBySms(command));
    }

    @PostMapping("/refresh")
    @Operation(summary = "刷新 token", description = "凭 refresh_token 换新 access + id + refresh（refresh 一次性轮换，凭 refresh_token 本身鉴权）")
    public ApiResponse<LoginResponse> refresh(@Valid @RequestBody RefreshTokenCommand command) {
        return ApiResponse.ok(tokenAppService.refresh(command.refreshToken()));
    }

    @PostMapping("/logout")
    @RequireAuth
    @Operation(summary = "登出", description = "清除当前服务端会话")
    public ApiResponse<Void> logout() {
        loginAppService.logout();
        return ApiResponse.ok();
    }

    @PostMapping("/reset-password")
    @Operation(summary = "重置密码", description = "经手机/邮箱验证码重置密码，重置后踢出所有会话")
    public ApiResponse<Void> resetPassword(@Valid @RequestBody ResetPasswordCommand command) {
        passwordAppService.resetPassword(command);
        return ApiResponse.ok();
    }

    @PostMapping("/change-password")
    @RequireAuth
    @Operation(summary = "修改密码", description = "验证旧密码后修改，修改后踢出所有会话")
    public ApiResponse<Void> changePassword(@Valid @RequestBody ChangePasswordCommand command) {
        passwordAppService.changePassword(command);
        return ApiResponse.ok();
    }

    @GetMapping("/me")
    @RequireAuth
    @Operation(summary = "当前登录用户", description = "返回当前登录用户的 profile（供应用界面显示「我是谁」，issue #12）")
    public ApiResponse<AccountProfileResponse> getCurrentUser() {
        return ApiResponse.ok(profileAppService.getCurrentProfile());
    }

    @GetMapping("/profile")
    @RequireAuth
    @Operation(summary = "查看个人资料", description = "返回当前登录用户的 profile")
    public ApiResponse<AccountProfileResponse> getProfile() {
        return ApiResponse.ok(profileAppService.getCurrentProfile());
    }

    @PutMapping("/profile")
    @RequireAuth
    @Operation(summary = "编辑个人资料", description = "更新当前登录用户的昵称/头像（仅非空字段）")
    public ApiResponse<Void> updateProfile(@Valid @RequestBody UpdateProfileCommand command) {
        profileAppService.updateCurrentProfile(command);
        return ApiResponse.ok();
    }
}
