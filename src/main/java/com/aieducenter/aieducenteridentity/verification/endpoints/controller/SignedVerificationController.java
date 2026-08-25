package com.aieducenter.aieducenteridentity.verification.endpoints.controller;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aieducenter.aieducenteridentity.shared.error.SharedErrorCode;
import com.aieducenter.aieducenteridentity.verification.application.VerificationCodeAppService;
import com.aieducenter.aieducenteridentity.verification.application.dto.CodeTarget;
import com.aieducenter.aieducenteridentity.verification.application.dto.CodeVerificationView;
import com.aieducenter.aieducenteridentity.verification.application.dto.SendCodeResponse;
import com.aieducenter.aieducenteridentity.verification.application.dto.SendEmailCodeCommand;
import com.aieducenter.aieducenteridentity.verification.application.dto.SendSmsCodeCommand;
import com.aieducenter.aieducenteridentity.verification.application.dto.SignedSendCodeRequest;
import com.aieducenter.aieducenteridentity.verification.application.dto.SignedVerifyCodeRequest;
import com.aieducenter.aieducenteridentity.verification.application.dto.VerifyCodeCommand;
import com.aieducenter.aieducenteridentity.verification.application.dto.VerifySmsCodeCommand;
import com.aieducenter.aieducenteridentity.verification.domain.error.VerificationCodeError;
import com.cartisan.core.exception.DomainException;
import com.cartisan.openapi.annotation.RequireSignature;
import com.cartisan.web.doc.ErrorCodes;
import com.cartisan.web.response.ApiResponse;
import com.cartisan.web.util.IpUtil;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * 验证码签名服务端点——{@code /api/verification-codes}（CONTEXT「签名服务 API」/ ADR-0009 / #56 / #62）。
 *
 * <p>薄壳 adapter：签名 gate（{@code @RequireSignature}，cartisan-openapi 拦截器 + RemoteApiKeyProvider
 * 解析调用方）→ 委托本 bc 的 {@link VerificationCodeAppService}，<b>不重写、不重测领域逻辑</b>。
 * 与 SSO 浏览器闭环（{@code /api/sso/verification-code/*}，{@code SsoVerificationCodeController}）
 * 同源 AppService、不同 namespace——本 namespace 专给机机签名调用。</p>
 *
 * <p>统一渠道判别 {@code target}（{@link CodeTarget}）：EMAIL 走 {@code sendEmail/verifyCode}，
 * SMS 走 {@code sendSms/verifyPhoneCode}。发码 ip 从 {@link HttpServletRequest} 取（{@link IpUtil#getClientIp}，
 * 对齐 payment）。</p>
 *
 * <p><b>设计原则</b>（与 SSO 浏览器闭环不同）：① 不强制图形码——调用方可信，防轰炸靠限流
 * （per apiKey + per target），{@code captchaId/captchaCode} 可选（提供则校验）；② 裸验码不绑操作、
 * 老实回 {@code valid}（不搬浏览器防用户枚举）；③ 错误响应走标准 cartisan-web {@link ApiResponse}
 * （非 OIDC {@code {error, error_description}}），缺失 / 错签名由框架返回 401。</p>
 *
 * <p>类级 {@code @RequireSignature}：本 controller 所有端点皆需签名（拦截器取方法注解兜类注解）。</p>
 *
 * <p><b>错误码契约</b>（#77）：约定全文见 {@code SignedAccountController} 类 javadoc（只列业务码，
 * gate 401/403 与通用参数校验不逐端点声明）。本类特例：{@code verify} 的码错 / 过期 / 已用
 * <b>不是</b>错误响应（老实降级 {@code valid=false}），故不进其 {@code @ErrorCodes}——只有格式 /
 * purpose 错抛 400。</p>
 *
 * @since 0.1.0
 */
@RestController
@RequestMapping("/api/verification-codes")
@RequireSignature
@Validated
@Tag(name = "Verification / Signed", description = "验证码签名服务（机机，@RequireSignature）")
public class SignedVerificationController {

    private final VerificationCodeAppService verificationCodeAppService;

    public SignedVerificationController(VerificationCodeAppService verificationCodeAppService) {
        this.verificationCodeAppService = verificationCodeAppService;
    }

    @PostMapping
    @Operation(summary = "发码（邮箱 / 短信）",
        description = "签名调用方凭 target（EMAIL/SMS）+ value（邮箱 / 手机号）+ purpose 下发验证码，"
            + "返回 SendCodeResponse。图形码 captchaId/captchaCode 可选——不强制（调用方可信、防轰炸靠限流），"
            + "SMS 提供则校验（错 → 400）。格式 / purpose 错 → 400；触发限流（per target / per ip）→ 429。"
            + "ip 从请求取（限流 per ip）。")
    @ErrorCodes({"VERIFICATION_EMAIL_INVALID", "VERIFICATION_PHONE_INVALID", "CAPTCHA_INVALID",
        "VERIFICATION_PURPOSE_INVALID", "VERIFICATION_RATE_LIMIT_EMAIL", "VERIFICATION_RATE_LIMIT_PHONE",
        "VERIFICATION_RATE_LIMIT_IP"})
    public ApiResponse<SendCodeResponse> sendCode(@Valid @RequestBody SignedSendCodeRequest request,
            HttpServletRequest httpRequest) {
        String ip = IpUtil.getClientIp(httpRequest);
        SendCodeResponse response = switch (request.target()) {
            case EMAIL -> verificationCodeAppService.sendEmailVerificationCode(
                new SendEmailCodeCommand(request.value(), request.purpose()), ip);
            case SMS -> verificationCodeAppService.sendSmsVerificationCode(
                new SendSmsCodeCommand(request.value(), request.purpose(),
                    request.captchaId(), request.captchaCode()), ip);
        };
        return ApiResponse.ok(response);
    }

    @PostMapping("/verify")
    @Operation(summary = "裸验码（不绑操作）",
        description = "签名调用方凭 target + value + code + purpose 裸验一个验证码，返回 {valid}。"
            + "码对 → valid=true（码即标记已用）；码错 / 过期 / 已用 → valid=false。调用方可信，老实回 valid，"
            + "不搬浏览器防用户枚举。格式 / purpose 错仍抛标准错误（400）。")
    @ErrorCodes({"VERIFICATION_EMAIL_INVALID", "VERIFICATION_PHONE_INVALID",
        "VERIFICATION_PURPOSE_INVALID"})
    public ApiResponse<CodeVerificationView> verify(@Valid @RequestBody SignedVerifyCodeRequest request) {
        boolean valid;
        try {
            switch (request.target()) {
                case EMAIL -> verificationCodeAppService.verifyCode(
                    new VerifyCodeCommand(request.value(), request.code(), request.purpose()));
                case SMS -> verificationCodeAppService.verifyPhoneCode(
                    new VerifySmsCodeCommand(request.value(), request.code(), request.purpose()));
            }
            valid = true;
        } catch (DomainException ex) {
            // 码错 / 过期 / 已用 → 老实降级 valid=false；格式错（EMAIL/PHONE_INVALID）如实抛 400
            if (isCodeMismatch(ex)) {
                valid = false;
            } else {
                throw ex;
            }
        }
        return ApiResponse.ok(new CodeVerificationView(valid));
    }

    private static boolean isCodeMismatch(DomainException ex) {
        String code = ex.getCodeMessage().code();
        return SharedErrorCode.VERIFICATION_CODE_INVALID.code().equals(code)
            || VerificationCodeError.CODE_EXPIRED.code().equals(code)
            || VerificationCodeError.CODE_ALREADY_USED.code().equals(code);
    }
}
