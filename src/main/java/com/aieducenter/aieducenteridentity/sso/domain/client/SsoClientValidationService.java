package com.aieducenter.aieducenteridentity.sso.domain.client;

import com.aieducenter.aieducenteridentity.sso.domain.error.OidcException;
import com.aieducenter.aieducenteridentity.sso.domain.error.SsoError;
import com.cartisan.core.stereotype.DomainService;

/**
 * SSO 消费方校验领域服务——client 存活 + redirect_uri / post_logout_redirect_uri 精确匹配白名单的单一真相源
 * （CONTEXT 安全集）。
 *
 * <p>{@code /authorize} 与 {@code /api/sso/login} 两处发 code 前的 client/redirect 校验共用本服务，
 * 避免重复实现分叉（安全关键逻辑须只有一份）。redirect_uri 与 post_logout_redirect_uri 各走独立白名单
 * （ADR-0005），均精确匹配、不做前缀/通配；不匹配时不重定向（防开放重定向），抛 {@link OidcException}。</p>
 *
 * @since 0.1.0
 */
@DomainService
public class SsoClientValidationService {

    private final SsoClientRepository clientRepository;

    public SsoClientValidationService(SsoClientRepository clientRepository) {
        this.clientRepository = clientRepository;
    }

    /**
     * 按 client_id 解析消费方；缺失/不存在/停用 → {@link SsoError#UNAUTHORIZED_CLIENT}。
     */
    public SsoClient requireActiveClient(String clientId) {
        if (clientId == null || clientId.isBlank()) {
            throw new OidcException(SsoError.UNAUTHORIZED_CLIENT, "client_id 缺失");
        }
        return clientRepository.findByClientId(clientId)
            .orElseThrow(() -> new OidcException(SsoError.UNAUTHORIZED_CLIENT, "client_id 无效或未注册"));
    }

    /**
     * 校验 redirect_uri 在登录回调白名单内（精确匹配）；缺失/不在白名单 → {@link SsoError#INVALID_REQUEST}。
     */
    public void requireRedirectUri(SsoClient client, String redirectUri) {
        if (redirectUri == null || redirectUri.isBlank()) {
            throw new OidcException(SsoError.INVALID_REQUEST, "redirect_uri 缺失");
        }
        if (!client.hasRedirectUri(redirectUri)) {
            throw new OidcException(SsoError.INVALID_REQUEST, "redirect_uri 未登记");
        }
    }

    /**
     * 校验 post_logout_redirect_uri 在登出回跳白名单内（精确匹配，不复用 redirect_uri，ADR-0005）；
     * 缺失/不在白名单 → {@link SsoError#INVALID_REQUEST}。
     */
    public void requirePostLogoutRedirectUri(SsoClient client, String postLogoutRedirectUri) {
        if (postLogoutRedirectUri == null || postLogoutRedirectUri.isBlank()) {
            throw new OidcException(SsoError.INVALID_REQUEST, "post_logout_redirect_uri 缺失");
        }
        if (!client.hasPostLogoutRedirectUri(postLogoutRedirectUri)) {
            throw new OidcException(SsoError.INVALID_REQUEST, "post_logout_redirect_uri 未登记");
        }
    }
}
