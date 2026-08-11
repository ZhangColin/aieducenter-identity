package com.aieducenter.aieducenteridentity.account.endpoints.controller;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aieducenter.aieducenteridentity.account.application.AccountAuthAppService;
import com.aieducenter.aieducenteridentity.account.application.dto.command.AuthenticateCommand;
import com.aieducenter.aieducenteridentity.account.application.dto.response.SubjectView;
import com.cartisan.openapi.annotation.RequireSignature;
import com.cartisan.web.response.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * 账号签名服务端点——{@code /api/account/*}（CONTEXT「签名服务 API」/ ADR-0009 / #56 / #58）。
 *
 * <p>薄壳 adapter：签名 gate（{@code @RequireSignature}，cartisan-openapi 拦截器 + RemoteApiKeyProvider
 * 解析调用方）→ 委托本 bc 的 {@link AccountAuthAppService}，<b>不重写、不重测领域逻辑</b>。登录只回
 * {@link SubjectView}（不发 token、不建 identity 侧会话——「登录态 / session」是调用方自己的概念）。
 * cookie / public 自助端点已在 {@code /api/sso/*}（{@code SsoAccountController}），本 namespace 专给签名服务。</p>
 *
 * <p>类级 {@code @RequireSignature}：本 controller 所有端点皆需签名（拦截器取方法注解兜类注解）。
 * 错误响应走标准 cartisan-web {@link ApiResponse}（非 OIDC {@code {error, error_description}}）；
 * 缺失 / 错签名由框架返回 401。</p>
 *
 * <p>本类是签名服务的 tracer bullet（#58）：authenticate 端点 + 骨架就位，后续 register /
 * authenticate-by-code / reset-password / {@code {userId}} 等端点（#59-#62）复制此模式。</p>
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

    public SignedAccountController(AccountAuthAppService authAppService) {
        this.authAppService = authAppService;
    }

    @PostMapping("/authenticate")
    @Operation(summary = "密码验密登录",
        description = "签名调用方凭 email/phone + 密码验密，返回 SubjectView（不发 token、不建会话）。"
            + "账号不存在与密码错统一返 401 ACCOUNT_009（防用户枚举）。")
    public ApiResponse<SubjectView> authenticate(@Valid @RequestBody AuthenticateCommand command) {
        return ApiResponse.ok(authAppService.authenticate(command.identifier(), command.password()));
    }
}
