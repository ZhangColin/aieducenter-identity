package com.aieducenter.aieducenteridentity.sso.config;

import java.util.List;

import cn.hutool.core.collection.CollUtil;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.aieducenter.aieducenteridentity.sso.infrastructure.token.JwtTokenProperties;

/**
 * 环境绝对 URL 启动防护（issue #74）。
 *
 * <p>issuer（{@code identity.token.jwt.issuer}）与登录页 / 错误页 URL（{@code identity.sso.login-page-url} /
 * {@code identity.sso.error-page-url}）是环境事实——base 配置与配置类默认值已清零，必须由各 profile
 * 显式提供。任意 profile 下三者任一为空（含 prod 裸 {@code ${ENV}} 占位符在 env 缺失时穿透成未解析
 * 字面量——binder 对占位符是 non-strict，不抛、保留原样）→ 启动 fail-fast 并指明缺失键，防止签出
 * {@code iss=null} 的垃圾 token、浏览器端点 302 到空地址（沿用 #29 DevCodeProdGuard 的启动期硬防线模式）。</p>
 */
@Component
public class EnvironmentUrlGuard {

    static final String ISSUER_KEY = "identity.token.jwt.issuer";
    static final String LOGIN_PAGE_URL_KEY = "identity.sso.login-page-url";
    static final String ERROR_PAGE_URL_KEY = "identity.sso.error-page-url";

    private final JwtTokenProperties tokenProperties;
    private final SsoProperties ssoProperties;

    public EnvironmentUrlGuard(JwtTokenProperties tokenProperties, SsoProperties ssoProperties) {
        this.tokenProperties = tokenProperties;
        this.ssoProperties = ssoProperties;
    }

    @PostConstruct
    void checkEnvironmentUrlsPresent() {
        List<String> missing = CollUtil.newArrayList();
        if (isMissing(tokenProperties.getIssuer())) {
            missing.add(ISSUER_KEY);
        }
        if (isMissing(ssoProperties.getLoginPageUrl())) {
            missing.add(LOGIN_PAGE_URL_KEY);
        }
        if (isMissing(ssoProperties.getErrorPageUrl())) {
            missing.add(ERROR_PAGE_URL_KEY);
        }
        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                "环境绝对 URL 缺失（issuer/登录页/错误页须由各 profile 显式配置，base 与配置类不带默认值；"
                    + "prod 下即对应 env 未设置），请补配置键：[" + String.join(", ", missing) + "]");
        }
    }

    /** 空/空白，或含 {@code ${}（裸占位符未解析穿透，值不是真实 URL）。 */
    private static boolean isMissing(String value) {
        return !StringUtils.hasText(value) || value.contains("${");
    }
}
