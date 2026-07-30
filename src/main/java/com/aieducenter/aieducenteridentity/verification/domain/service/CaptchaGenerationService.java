package com.aieducenter.aieducenteridentity.verification.domain.service;

import cn.hutool.captcha.LineCaptcha;
import com.cartisan.core.stereotype.DomainService;

/**
 * 图形验证码生成领域服务。
 *
 * <h3>规则</h3>
 * <ul>
 *   <li>使用 hutool-captcha LineCaptcha</li>
 *   <li>130x40 尺寸，4 位字符</li>
 *   <li>返回 base64 编码的图片</li>
 * </ul>
 */
@DomainService
public class CaptchaGenerationService {

    private static final int WIDTH = 130;
    private static final int HEIGHT = 40;
    private static final int CODE_COUNT = 4;
    private static final int LINE_COUNT = 20;

    /**
     * 生成图形验证码。
     *
     * @return 验证码结果（图片 base64 和验证码文本）
     */
    public CaptchaResult generate() {
        LineCaptcha captcha = new LineCaptcha(WIDTH, HEIGHT, CODE_COUNT, LINE_COUNT);

        String image = "data:image/png;base64," + captcha.getImageBase64();
        String code = captcha.getCode();

        return new CaptchaResult(image, code);
    }

    /**
     * 图形验证码生成结果。
     *
     * @param image base64 编码的图片（带 data:image/png;base64, 前缀）
     * @param code 验证码文本
     */
    public record CaptchaResult(String image, String code) {}
}
