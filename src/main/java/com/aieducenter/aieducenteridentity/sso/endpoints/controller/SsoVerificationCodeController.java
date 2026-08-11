package com.aieducenter.aieducenteridentity.sso.endpoints.controller;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aieducenter.aieducenteridentity.verification.application.VerificationCodeAppService;
import com.aieducenter.aieducenteridentity.verification.application.dto.SendCodeResponse;
import com.aieducenter.aieducenteridentity.verification.application.dto.SendEmailCodeCommand;
import com.aieducenter.aieducenteridentity.verification.application.dto.SendSmsCodeCommand;
import com.aieducenter.aieducenteridentity.verification.application.dto.VerifyCodeCommand;
import com.aieducenter.aieducenteridentity.verification.application.dto.VerifyCodeResult;
import com.cartisan.web.response.ApiResponse;
import com.cartisan.web.util.IpUtil;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 验证码公开端点——{@code /api/sso/verification-code/*}（CONTEXT 接口命名空间 / issue #55）。
 *
 * <p>SSO 浏览器闭环 controller：identity-web 注册/登录页发邮箱/短信码 + 裸验码，<b>公开</b>（不在
 * {@code identity.sso.protected-paths} 内）。跨 bc 调 verification 的 {@link VerificationCodeAppService}
 * （ADR-0007）。签名服务版的发码/验码（{@code /api/verification-codes}）归 verification bc 自管（#56）。</p>
 *
 * <p><b>历史错位修正</b>：原 {@code VerificationCodeController} 的 {@code @RequestMapping("/api/account")}
 * 把 path 寄生在 account namespace、包却在 verification bc——迁 namespace 时一并归正（path 与 controller
 * 归属都对齐 sso 浏览器闭环）。</p>
 */
@RestController
@RequestMapping("/api/sso/verification-code")
@Tag(name = "SSO / VerificationCode", description = "验证码（浏览器闭环·公开）")
public class SsoVerificationCodeController {

    private final VerificationCodeAppService verificationCodeAppService;

    public SsoVerificationCodeController(VerificationCodeAppService verificationCodeAppService) {
        this.verificationCodeAppService = verificationCodeAppService;
    }

    @PostMapping("/email")
    @Operation(summary = "发送邮箱验证码", description = "按 purpose（REGISTER/LOGIN/RESET_PASSWORD）下发，限流 per email+ip")
    public ApiResponse<SendCodeResponse> sendEmailVerificationCode(@RequestBody SendEmailCodeCommand command,
            HttpServletRequest request) {
        String ip = IpUtil.getClientIp(request);
        return ApiResponse.ok(verificationCodeAppService.sendEmailVerificationCode(command, ip));
    }

    @PostMapping("/sms")
    @Operation(summary = "发送短信验证码", description = "需图形码（captchaId/captchaCode，防轰炸）+ purpose；限流 per phone+ip")
    public ApiResponse<SendCodeResponse> sendSmsVerificationCode(@RequestBody SendSmsCodeCommand command,
            HttpServletRequest request) {
        String ip = IpUtil.getClientIp(request);
        return ApiResponse.ok(verificationCodeAppService.sendSmsVerificationCode(command, ip));
    }

    @PostMapping("/verify")
    @Operation(summary = "校验验证码", description = "裸验码（不绑操作）；调用方验过后自行决定下一步")
    public ApiResponse<VerifyCodeResult> verifyCode(@RequestBody VerifyCodeCommand command) {
        return ApiResponse.ok(verificationCodeAppService.verifyCode(command));
    }
}
