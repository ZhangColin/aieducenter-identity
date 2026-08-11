package com.aieducenter.aieducenteridentity.account.endpoints.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aieducenter.aieducenteridentity.account.application.AccountAuthAppService;
import com.aieducenter.aieducenteridentity.account.application.AccountPasswordAppService;
import com.aieducenter.aieducenteridentity.account.application.dto.command.AuthenticateByCodeCommand;
import com.aieducenter.aieducenteridentity.account.application.dto.command.AuthenticateCommand;
import com.aieducenter.aieducenteridentity.account.application.dto.command.RegisterByCodeCommand;
import com.aieducenter.aieducenteridentity.account.application.dto.command.ResetPasswordByCodeCommand;
import com.aieducenter.aieducenteridentity.account.application.dto.command.ResetPasswordCommand;
import com.aieducenter.aieducenteridentity.account.application.dto.response.SubjectView;
import com.cartisan.openapi.annotation.RequireSignature;
import com.cartisan.web.response.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * 账号签名服务端点——{@code /api/account/*}（CONTEXT「签名服务 API」/ ADR-0009 / #56）。
 *
 * <p>薄壳 adapter：签名 gate（{@code @RequireSignature}，cartisan-openapi 拦截器 + RemoteApiKeyProvider
 * 解析调用方）→ 委托本 bc 的 {@link AccountAuthAppService} / {@link AccountPasswordAppService}，
 * <b>不重写、不重测领域逻辑</b>。登录只回 {@link SubjectView}（不发 token、不建 identity 侧会话——
 * 「登录态 / session」是调用方自己的概念）。cookie / public 自助端点已在 {@code /api/sso/*}
 * （{@code SsoAccountController}），本 namespace 专给签名服务。</p>
 *
 * <p>类级 {@code @RequireSignature}：本 controller 所有端点皆需签名（拦截器取方法注解兜类注解）。
 * 错误响应走标准 cartisan-web {@link ApiResponse}（非 OIDC {@code {error, error_description}}）；
 * 缺失 / 错签名由框架返回 401。</p>
 *
 * <p>落地分期：#58 打通骨架 + {@code authenticate}（tracer bullet）；#59 加按 identifier 三端点
 * （{@code authenticate-by-code} / {@code register} / {@code reset-password}）；{@code {userId}} 读写类端点见后续 ticket。</p>
 *
 * @since 0.1.0
 */
@RestController
@RequestMapping("/api/account")
@RequireSignature
@Validated
@Tag(name = "Account / Signed", description = "账号签名服务（机机，@RequireSignature）")
public class SignedAccountController {

    private final AccountAuthAppService authAppService;
    private final AccountPasswordAppService passwordAppService;

    public SignedAccountController(AccountAuthAppService authAppService,
            AccountPasswordAppService passwordAppService) {
        this.authAppService = authAppService;
        this.passwordAppService = passwordAppService;
    }

    @PostMapping("/authenticate")
    @Operation(summary = "密码验密登录",
        description = "签名调用方凭 email/phone + 密码验密，返回 SubjectView（不发 token、不建会话）。"
            + "账号不存在与密码错统一返 401 ACCOUNT_009（防用户枚举）。")
    public ApiResponse<SubjectView> authenticate(@Valid @RequestBody AuthenticateCommand command) {
        return ApiResponse.ok(authAppService.authenticate(command.identifier(), command.password()));
    }

    @PostMapping("/authenticate-by-code")
    @Operation(summary = "验证码登录",
        description = "签名调用方凭 email/phone + 验证码登录，返回 SubjectView（不发 token、不建会话）。"
            + "先验码（purpose=LOGIN）再委托 authenticateByIdentifier；码错 → 标准错误响应。")
    public ApiResponse<SubjectView> authenticateByCode(@Valid @RequestBody AuthenticateByCodeCommand command) {
        return ApiResponse.ok(authAppService.authenticateByCode(command.identifier(), command.code()));
    }

    @PostMapping("/register")
    @Operation(summary = "注册",
        description = "签名调用方凭 email 或 phone + 验过的码（+ 可选密码）注册到中央 Account，返回 SubjectView"
            + "（注册即登录，不发 token、不建会话）。先验码（purpose=REGISTER）再委托 register；"
            + "码错 / 唯一性冲突 → 标准错误响应。")
    public ApiResponse<SubjectView> register(@Valid @RequestBody RegisterByCodeCommand command) {
        return ApiResponse.ok(authAppService.registerByCode(command));
    }

    @PostMapping("/reset-password")
    @Operation(summary = "重置密码",
        description = "签名调用方凭 identifier + 验证码重置密码，委托 resetPassword（内部验码 purpose=RESET_PASSWORD + "
            + "改密 + 踢会话）。成功 → 204；码错 → 标准错误响应。")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordByCodeCommand command) {
        passwordAppService.resetPassword(new ResetPasswordCommand(
            command.identifier(), command.code(), command.newPassword()));
        return ResponseEntity.noContent().build();
    }
}
