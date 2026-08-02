package com.aieducenter.aieducenteridentity.verification.domain.service;

import cn.hutool.captcha.LineCaptcha;
import cn.hutool.captcha.generator.CodeGenerator;

import com.aieducenter.aieducenteridentity.verification.config.CaptchaProperties;
import com.cartisan.core.stereotype.DomainService;

/**
 * 图形验证码生成领域服务。
 *
 * <h3>规则</h3>
 * <ul>
 *   <li>使用 hutool-captcha LineCaptcha</li>
 *   <li>130x40 尺寸，4 位字符</li>
 *   <li>返回 base64 编码的图片</li>
 *   <li>dev 固定图形码（issue #29）：{@code verification.captcha.dev-code} 非空时以固定值
 *   作为生成源——图上画的就是固定串（人肉 QA 无感），返回文本 = 固定值，照常存 Redis 比对</li>
 * </ul>
 */
@DomainService
public class CaptchaGenerationService {

    private static final int WIDTH = 130;
    private static final int HEIGHT = 40;
    private static final int CODE_COUNT = 4;
    private static final int LINE_COUNT = 20;

    private final String devCode;

    public CaptchaGenerationService(CaptchaProperties properties) {
        this.devCode = properties.getDevCode();
    }

    /**
     * 生成图形验证码。
     *
     * @return 验证码结果（图片 base64 和验证码文本）
     */
    public CaptchaResult generate() {
        // hutool 5.8.34 无 setCode：固定分支以固定值 CodeGenerator 作生成源，
        // code 与图片同源（getImageBase64 内部按 code 绘制），等价于「setCode 后再画图」
        LineCaptcha captcha = hasText(devCode)
            ? new LineCaptcha(WIDTH, HEIGHT, new FixedCodeGenerator(devCode), LINE_COUNT)
            : new LineCaptcha(WIDTH, HEIGHT, CODE_COUNT, LINE_COUNT);

        String image = "data:image/png;base64," + captcha.getImageBase64();
        String code = captcha.getCode();

        return new CaptchaResult(image, code);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    /**
     * 固定值生成源（dev 固定码专用）。{@link CodeGenerator#verify} 本服务不用
     * （图形码比对走 Redis 明文，见 CaptchaAppService），给标准相等实现即可。
     */
    private record FixedCodeGenerator(String code) implements CodeGenerator {
        @Override
        public String generate() {
            return code;
        }

        @Override
        public boolean verify(String code, String userInputCode) {
            return this.code.equals(userInputCode);
        }
    }

    /**
     * 图形验证码生成结果。
     *
     * @param image base64 编码的图片（带 data:image/png;base64, 前缀）
     * @param code 验证码文本
     */
    public record CaptchaResult(String image, String code) {}
}
