package com.aieducenter.aieducenteridentity.verification.config;

import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import jakarta.annotation.PostConstruct;

/**
 * dev 固定码 prod 防误开（issue #29）。
 *
 * <p>prod profile 下配置了任一 dev 固定码键（{@code verification.code.dev-code} /
 * {@code verification.captcha.dev-code}）→ 启动 fail-fast，错误消息指明具体键名。
 * 相比 {@code @ConditionalOnProperty} 的「prod 永不开靠纪律」，这里刻意升级为启动期硬防线。</p>
 */
@Component
public class DevCodeProdGuard {

    static final String CODE_DEV_KEY = "verification.code.dev-code";
    static final String CAPTCHA_DEV_KEY = "verification.captcha.dev-code";

    private final Environment environment;
    private final VerificationCodeProperties codeProperties;
    private final CaptchaProperties captchaProperties;

    public DevCodeProdGuard(Environment environment,
            VerificationCodeProperties codeProperties,
            CaptchaProperties captchaProperties) {
        this.environment = environment;
        this.codeProperties = codeProperties;
        this.captchaProperties = captchaProperties;
    }

    @PostConstruct
    void checkDevCodeNotInProd() {
        if (!environment.acceptsProfiles(Profiles.of("prod"))) {
            return;
        }
        StringBuilder keys = new StringBuilder();
        if (StringUtils.hasText(codeProperties.getDevCode())) {
            keys.append(CODE_DEV_KEY);
        }
        if (StringUtils.hasText(captchaProperties.getDevCode())) {
            if (keys.length() > 0) {
                keys.append(", ");
            }
            keys.append(CAPTCHA_DEV_KEY);
        }
        if (keys.length() > 0) {
            throw new IllegalStateException(
                "prod profile 下禁止配置 dev 固定码（生成处固定仅限开发/测试环境），请移除配置键：[" + keys + "]");
        }
    }
}
