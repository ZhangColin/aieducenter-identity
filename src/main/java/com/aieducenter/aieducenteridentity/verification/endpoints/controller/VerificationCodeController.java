package com.aieducenter.aieducenteridentity.verification.endpoints.controller;

import com.aieducenter.aieducenteridentity.verification.application.VerificationCodeAppService;
import com.cartisan.web.util.IpUtil;
import com.aieducenter.aieducenteridentity.verification.application.dto.SendCodeResponse;
import com.aieducenter.aieducenteridentity.verification.application.dto.SendEmailCodeCommand;
import com.aieducenter.aieducenteridentity.verification.application.dto.SendSmsCodeCommand;
import com.aieducenter.aieducenteridentity.verification.application.dto.VerifyCodeCommand;
import com.aieducenter.aieducenteridentity.verification.application.dto.VerifyCodeResult;
import com.cartisan.web.response.ApiResponse;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 验证码控制器。
 *
 * @since 0.1.0
 */
@RestController
@RequestMapping("/api/account")
public class VerificationCodeController {

    private final VerificationCodeAppService service;

    public VerificationCodeController(VerificationCodeAppService service) {
        this.service = service;
    }

    /**
     * 发送邮箱验证码。
     *
     * @param command 命令
     * @param request HTTP请求
     * @return 响应
     */
    @PostMapping("/verification-code/email")
    public ApiResponse<SendCodeResponse> sendEmailVerificationCode(
            @RequestBody SendEmailCodeCommand command,
            HttpServletRequest request) {

        String ip = IpUtil.getClientIp(request);
        return ApiResponse.ok(service.sendEmailVerificationCode(command, ip));
    }

    /**
     * 发送短信验证码。
     *
     * @param command 命令
     * @param request HTTP请求
     * @return 响应
     */
    @PostMapping("/verification-code/sms")
    public ApiResponse<SendCodeResponse> sendSmsVerificationCode(
            @RequestBody SendSmsCodeCommand command,
            HttpServletRequest request) {

        String ip = IpUtil.getClientIp(request);
        return ApiResponse.ok(service.sendSmsVerificationCode(command, ip));
    }

    /**
     * 校验验证码。
     *
     * @param command 命令
     * @return 响应
     */
    @PostMapping("/verify-code")
    public ApiResponse<VerifyCodeResult> verifyCode(@RequestBody VerifyCodeCommand command) {
        return ApiResponse.ok(service.verifyCode(command));
    }
}
