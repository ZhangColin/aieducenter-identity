package com.aieducenter.aieducenteridentity.verification.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 验证码配置属性。
 *
 * @since 0.1.0
 */
@Component
@ConfigurationProperties(prefix = "verification.code")
public class VerificationCodeProperties {

    /**
     * 验证码有效期（分钟）。
     */
    private int expireMinutes = 5;

    /**
     * 邮箱限流：同一邮箱发送间隔（秒）。
     */
    private long emailCooldownSeconds = 60;

    /**
     * 手机限流：同一手机发送间隔（秒）。
     */
    private long phoneCooldownSeconds = 60;

    /**
     * IP 限流：每小时最大发送次数。
     */
    private int ipMaxPerHour = 10;

    /**
     * IP 限流时间窗口（小时）。
     */
    private Duration ipLimitWindow = Duration.ofHours(1);

    /**
     * dev 固定验证码（issue #29）：非空时生成器直接返回该值（生成处固定，比对路径全真），
     * 供不依赖真实收码的联调/QA/e2e 使用。默认空 = 禁用。prod profile 配置将被
     * {@link DevCodeProdGuard} 拒绝启动。
     */
    private String devCode = "";

    public int getExpireMinutes() {
        return expireMinutes;
    }

    public void setExpireMinutes(int expireMinutes) {
        this.expireMinutes = expireMinutes;
    }

    public long getEmailCooldownSeconds() {
        return emailCooldownSeconds;
    }

    public void setEmailCooldownSeconds(long emailCooldownSeconds) {
        this.emailCooldownSeconds = emailCooldownSeconds;
    }

    public long getPhoneCooldownSeconds() {
        return phoneCooldownSeconds;
    }

    public void setPhoneCooldownSeconds(long phoneCooldownSeconds) {
        this.phoneCooldownSeconds = phoneCooldownSeconds;
    }

    public int getIpMaxPerHour() {
        return ipMaxPerHour;
    }

    public void setIpMaxPerHour(int ipMaxPerHour) {
        this.ipMaxPerHour = ipMaxPerHour;
    }

    public Duration getIpLimitWindow() {
        return ipLimitWindow;
    }

    public void setIpLimitWindow(Duration ipLimitWindow) {
        this.ipLimitWindow = ipLimitWindow;
    }

    public String getDevCode() {
        return devCode;
    }

    public void setDevCode(String devCode) {
        this.devCode = devCode;
    }
}
