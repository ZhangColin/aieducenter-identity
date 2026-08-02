package com.aieducenter.aieducenteridentity.sso.application;

import java.util.Optional;

import org.springframework.stereotype.Service;

import com.aieducenter.aieducenteridentity.sso.application.dto.AuthorizeRequest;
import com.aieducenter.aieducenteridentity.sso.config.SsoProperties;
import com.aieducenter.aieducenteridentity.sso.domain.client.SsoClient;
import com.aieducenter.aieducenteridentity.sso.domain.client.SsoClientValidationService;
import com.aieducenter.aieducenteridentity.sso.domain.error.OidcException;
import com.aieducenter.aieducenteridentity.sso.domain.session.SsoSession;
import com.aieducenter.aieducenteridentity.sso.domain.session.SsoSessionRepository;

/**
 * /authorize 状态机（CONTEXT 登录契约 / issue #15）。
 *
 * <p>校验 client/redirect_uri（精确匹配白名单；重定向前错抛 {@link OidcException} 不重定向，防开放重定向）→
 * 有有效 SSO 会话则发 code 302 回 redirect_uri（二次 SSO 免登），否则 302 到登录页透传 authorize 参数。</p>
 *
 * <p>免登判定走显式 sessionId 查询（不依赖 RequestContext 绑定），由控制器从 SSO cookie 传入。</p>
 *
 * @since 0.1.0
 */
@Service
public class SsoAuthorizeAppService {

    private final SsoClientValidationService clientValidation;
    private final SsoSessionRepository sessionRepository;
    private final AuthorizationCodeAppService codeService;
    private final SsoProperties properties;

    public SsoAuthorizeAppService(SsoClientValidationService clientValidation, SsoSessionRepository sessionRepository,
            AuthorizationCodeAppService codeService, SsoProperties properties) {
        this.clientValidation = clientValidation;
        this.sessionRepository = sessionRepository;
        this.codeService = codeService;
        this.properties = properties;
    }

    /**
     * 处理 /authorize，返回应 302 跳转的目标地址。
     *
     * @param request   authorize 参数
     * @param sessionId 当前浏览器 SSO sessionId（可空）
     * @return 302 目标地址（有效会话 → redirect_uri?code&state；无 → 登录页透传参数）
     * @throws OidcException client/redirect_uri 无效（重定向前错，控制器渲染 400）
     */
    public String handleAuthorize(AuthorizeRequest request, String sessionId) {
        SsoClient client = clientValidation.requireActiveClient(request.clientId());
        clientValidation.requireRedirectUri(client, request.redirectUri());

        Optional<SsoSession> session =
            (sessionId != null && !sessionId.isBlank()) ? sessionRepository.findActive(sessionId) : Optional.empty();

        if (session.isPresent()) {
            SsoSession s = session.get();
            return codeService.issueCodeAndRedirect(
                s.userId(), client, request.redirectUri(), request.nonce(), request.scope(),
                s.sessionId(), request.state());
        }
        return buildLoginPageUrl(request);
    }

    private String buildLoginPageUrl(AuthorizeRequest request) {
        return AuthorizationCodeAppService.appendQuery(properties.getLoginPageUrl(),
            "client_id", request.clientId(),
            "redirect_uri", request.redirectUri(),
            "state", request.state(),
            "nonce", request.nonce(),
            "scope", request.scope());
    }
}
