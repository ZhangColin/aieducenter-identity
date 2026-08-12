package com.aieducenter.aieducenteridentity.account.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 后台管理配置属性（{@code identity.management.*}，ADR-0010 / #66 / #67）。
 *
 * <p>当前只一项：管理调用方白名单（过渡 stopgap，正式 per-app 权限见 #64）。
 * {@link ManagementCallerInterceptor} 比对 {@code RequestContext.getCallerAppName() ∈ authorizedCallers}，
 * 非白名单 → 403。默认 {@code admin-console}（admin BFF 在 app-registry 的 app_code）。</p>
 *
 * @since 0.1.0
 */
@Component
@ConfigurationProperties(prefix = "identity.management")
public class ManagementProperties {

    /**
     * 允许调管理端点的签名调用方 appName 白名单（对应 app-registry 的 app_code）。
     * 默认 {@code admin-console}；多调用方时追加。
     */
    private List<String> authorizedCallers = new ArrayList<>(List.of("admin-console"));

    public List<String> getAuthorizedCallers() {
        return authorizedCallers;
    }

    public void setAuthorizedCallers(List<String> authorizedCallers) {
        this.authorizedCallers = authorizedCallers;
    }

    /** 调用方 appName 是否在白名单内（null / 空白安全——null 不放行）。 */
    public boolean isAuthorized(String callerAppName) {
        if (callerAppName == null || callerAppName.isBlank()) {
            return false;
        }
        return authorizedCallers.contains(callerAppName);
    }
}
