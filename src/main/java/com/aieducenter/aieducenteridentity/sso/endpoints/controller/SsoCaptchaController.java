package com.aieducenter.aieducenteridentity.sso.endpoints.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aieducenter.aieducenteridentity.verification.application.CaptchaAppService;
import com.aieducenter.aieducenteridentity.verification.application.dto.CreateCaptchaResponse;
import com.cartisan.web.response.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 图形验证码公开端点——{@code GET /api/sso/captcha}（CONTEXT 接口命名空间 / issue #55）。
 *
 * <p>SSO 浏览器闭环 controller：identity-web 注册/登录页取图形码（防短信轰炸），<b>公开</b>（不在
 * {@code identity.sso.protected-paths} 内，SsoSessionFilter 放行）。跨 bc 调 verification 的
 * {@link CaptchaAppService}（ADR-0007 跨上下文只走应用层）。签名服务版的图形码（{@code /api/captchas}）
 * 归 verification bc 自管（#56，另一个 issue）。</p>
 */
@RestController
@RequestMapping("/api/sso")
@Tag(name = "SSO / Captcha", description = "图形验证码（浏览器闭环·公开）")
public class SsoCaptchaController {

    private final CaptchaAppService captchaAppService;

    public SsoCaptchaController(CaptchaAppService captchaAppService) {
        this.captchaAppService = captchaAppService;
    }

    @GetMapping("/captcha")
    @Operation(summary = "获取图形验证码", description = "返回图片 base64 + captchaId（一次性，Redis 3min）")
    public ApiResponse<CreateCaptchaResponse> getCaptcha() {
        return ApiResponse.ok(captchaAppService.createCaptcha());
    }
}
