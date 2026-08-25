package com.aieducenter.aieducenteridentity.verification.endpoints.controller;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aieducenter.aieducenteridentity.verification.application.CaptchaAppService;
import com.aieducenter.aieducenteridentity.verification.application.dto.CreateCaptchaResponse;
import com.cartisan.openapi.annotation.RequireSignature;
import com.cartisan.web.doc.ErrorCodes;
import com.cartisan.web.response.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 图形验证码签名服务端点——{@code POST /api/captchas}（CONTEXT「签名服务 API」/ ADR-0009 / #56 / #62）。
 *
 * <p>薄壳 adapter：签名 gate（{@code @RequireSignature}）→ 委托本 bc 的 {@link CaptchaAppService}，
 * <b>不重写、不重测领域逻辑</b>。与 SSO 浏览器闭环（{@code GET /api/sso/captcha}，
 * {@code SsoCaptchaController}）同源 AppService、不同 namespace——本端点专给机机签名调用方
 * 取图形码（可选地给短信发码加一层防脚本轰炸保护，调用方自决是否用）。</p>
 *
 * <p>类级 {@code @RequireSignature}：缺失 / 错签名由框架返回 401。</p>
 *
 * <p><b>错误码契约</b>（#77）：约定全文见 {@code SignedAccountController} 类 javadoc（只列业务码，
 * gate 401/403 与通用参数校验不逐端点声明）。本类特例：端点无业务错误码，声明
 * {@code INTERNAL_SERVER_ERROR} 占位（仅基建错可中断）。</p>
 *
 * @since 0.1.0
 */
@RestController
@RequestMapping("/api/captchas")
@RequireSignature
@Validated
@Tag(name = "Captcha / Signed", description = "图形验证码签名服务（机机，@RequireSignature）")
public class SignedCaptchaController {

    private final CaptchaAppService captchaAppService;

    public SignedCaptchaController(CaptchaAppService captchaAppService) {
        this.captchaAppService = captchaAppService;
    }

    @PostMapping
    @Operation(summary = "取图形验证码",
        description = "签名调用方取一次性图形码（base64 图片 + captchaId，Redis 3min）。"
            + "供可选地给短信发码加防脚本轰炸层——调用方自决是否带 captchaId/captchaCode 调发码。"
            + "无业务错误码（仅基建错可中断）。")
    @ErrorCodes("INTERNAL_SERVER_ERROR")
    public ApiResponse<CreateCaptchaResponse> createCaptcha() {
        return ApiResponse.ok(captchaAppService.createCaptcha());
    }
}
