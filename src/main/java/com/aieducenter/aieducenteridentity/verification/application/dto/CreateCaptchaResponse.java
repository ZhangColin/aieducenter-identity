package com.aieducenter.aieducenteridentity.verification.application.dto;

/**
 * 创建图形验证码响应。
 */
public record CreateCaptchaResponse(
    /**
     * base64 编码的图片（带 data:image/png;base64, 前缀）。
     */
    String image,

    /**
     * 验证码 ID，用于后续校验。
     */
    String captchaId
) {}
