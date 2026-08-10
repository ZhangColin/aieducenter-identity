package com.aieducenter.aieducenteridentity.sso.endpoints.web;

import java.net.URI;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import com.aieducenter.aieducenteridentity.sso.config.SsoProperties;
import com.aieducenter.aieducenteridentity.sso.domain.error.OidcException;

/**
 * 浏览器导航类端点（{@code /authorize}、{@code /logout}）错误 → 302 跳 identity-web 兜底页（ADR-0006）。
 *
 * <p>构造 {@code {errorPageUrl}?error&error_description&client_id}（client_id 可空则不带），集中 URL 拼装 +
 * 编码，供两个浏览器类 Controller 的局部 {@code @ExceptionHandler} 复用。跳转目标取自配置
 * （{@code identity.sso.error-page-url}）、<b>不取自请求参数</b>——防开放重定向（错误页目标不可由调用方控制）。</p>
 *
 * <p>机机类端点（{@code /token}、{@code /userinfo} 等）不走本类，仍由 {@link OidcExceptionHandler} 渲染 RFC6749 JSON。</p>
 *
 * @since 0.1.0
 */
@Component
public class OidcErrorPageRedirector {

    private final String errorPageUrl;

    public OidcErrorPageRedirector(SsoProperties properties) {
        this.errorPageUrl = properties.getErrorPageUrl();
    }

    /**
     * 构造 302 跳兜底页：透传 error code / description / client_id 给前端渲染。
     *
     * @param ex       OIDC 协议异常（提供 error code + description）
     * @param clientId 请求参数里的 client_id（可空——infra 503 / 缺参时可能无，空则不附带）
     */
    public ResponseEntity<Void> redirect(OidcException ex, String clientId) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(errorPageUrl)
            .queryParam("error", ex.error().code())
            .queryParam("error_description", ex.description());
        if (clientId != null && !clientId.isBlank()) {
            builder.queryParam("client_id", clientId);
        }
        URI location = builder.encode().build().toUri();
        return ResponseEntity.status(HttpStatus.FOUND).location(location).build();
    }
}
