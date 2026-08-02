package com.aieducenter.aieducenteridentity.verification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 图形验证码配置属性（issue #29）。
 */
@Component
@ConfigurationProperties(prefix = "verification.captcha")
public class CaptchaProperties {

    /**
     * dev 固定图形码：非空时生成器直接以该值绘码（生成处固定，图上画的就是固定串，
     * 人肉 QA 无感；比对路径全真）。默认空 = 禁用。prod profile 配置将被
     * {@link DevCodeProdGuard} 拒绝启动。
     */
    private String devCode = "";

    public String getDevCode() {
        return devCode;
    }

    public void setDevCode(String devCode) {
        this.devCode = devCode;
    }
}
