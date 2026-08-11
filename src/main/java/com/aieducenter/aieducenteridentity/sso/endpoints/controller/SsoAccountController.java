package com.aieducenter.aieducenteridentity.sso.endpoints.controller;

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
 * 账号自助 SSO 浏览器闭环端点——{@code /api/sso/*}（CONTEXT 接口命名空间 / issue #55）。
 *
 * <p>原 {@code /api/account/*}（cookie/public 版）迁来到 sso namespace，<b>腾空 {@code /api/account/*}
 * 给 account bc 的签名服务</b>（#56，另一个 issue）。跨 bc 调 account 的
 * {@link AccountPasswordAppService} / {@link AccountProfileAppService}（ADR-0007 跨上下文只走应用层）。</p>
 *
 * <p>鉴权分流：me / profile（GET·PUT）/ change-password 受 SSO cookie 保护（{@code identity.sso.protected-paths}，
 * SsoSessionFilter 凭 cookie 认人）；reset-password 公开（凭手机/邮箱验证码，不依赖会话）。认证入口
 * （login / login-code / register）在 {@code /api/sso/*} 的 {@code AuthController}。</p>
 */
@RestController
@RequestMapping("/api/sso")
@Validated
@Tag(name = "SSO / Account", description = "账号自助（浏览器闭环·cookie / public）")
public class SsoAccountController {

    private final AccountPasswordAppService passwordAppService;
    private final AccountProfileAppService profileAppService;

    public SsoAccountController(AccountPasswordAppService passwordAppService,
            AccountProfileAppService profileAppService) {
        this.passwordAppService = passwordAppService;
        this.profileAppService = profileAppService;
    }

    @PostMapping("/reset-password")
    @Operation(summary = "重置密码", description = "经手机/邮箱验证码重置密码，重置后踢出所有会话（公开，不依赖会话）")
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
