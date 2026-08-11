package com.aieducenter.aieducenteridentity.sso.application;

import org.springframework.stereotype.Service;

import com.aieducenter.aieducenteridentity.account.application.AccountSubjectAppService;
import com.aieducenter.aieducenteridentity.account.application.dto.response.SubjectStatus;
import com.aieducenter.aieducenteridentity.account.application.dto.response.SubjectView;
import com.aieducenter.aieducenteridentity.sso.application.dto.TokenRequest;
import com.aieducenter.aieducenteridentity.sso.application.dto.TokenResponse;
import com.aieducenter.aieducenteridentity.sso.application.dto.response.IssuedTokens;
import com.aieducenter.aieducenteridentity.sso.domain.client.ClientSecretVerifier;
import com.aieducenter.aieducenteridentity.sso.domain.client.SsoClient;
import com.aieducenter.aieducenteridentity.sso.domain.client.SsoClientRepository;
import com.aieducenter.aieducenteridentity.sso.domain.code.AuthorizationCodeStore;
import com.aieducenter.aieducenteridentity.sso.domain.code.IssuedAuthorizationCode;
import com.aieducenter.aieducenteridentity.sso.domain.error.OidcException;
import com.aieducenter.aieducenteridentity.sso.domain.error.SsoError;
import com.aieducenter.aieducenteridentity.sso.domain.session.SsoSessionRepository;
import com.aieducenter.aieducenteridentity.sso.domain.token.RefreshTokenPayload;
import com.cartisan.core.exception.DomainException;

/**
 * /token 端点应用服务（CONTEXT「token 归 /token」/ issue #15；ADR-0008：token 三件套归属 sso）。
 *
 * <p>authorization_code grant：验 client_secret → 一次性消费 code（验绑 client/redirect_uri）→ 签
 * access/id/refresh（id 回带 nonce）。refresh_token grant：验 client → 消费 refresh 轮换 → 重新签发。
 * token 三件套签发由 sso 内的 {@link TokenIssuerAppService} 完成；subject 数据经 {@link AccountSubjectAppService}
 * 取 {@link SubjectView}（ADR-0007：跨上下文只走应用层，不注入 account 仓储 / 不拿 account 聚合）。</p>
 *
 * @since 0.1.0
 */
@Service
public class SsoTokenAppService {

    private static final String GRANT_AUTHORIZATION_CODE = "authorization_code";
    private static final String GRANT_REFRESH_TOKEN = "refresh_token";

    private final SsoClientRepository clientRepository;
    private final ClientSecretVerifier clientSecretVerifier;
    private final AuthorizationCodeStore codeStore;
    private final TokenIssuerAppService tokenIssuer;
    private final AccountSubjectAppService accountSubjectAppService;
    private final SsoSessionRepository sessionRepository;

    public SsoTokenAppService(SsoClientRepository clientRepository, ClientSecretVerifier clientSecretVerifier,
            AuthorizationCodeStore codeStore, TokenIssuerAppService tokenIssuer,
            AccountSubjectAppService accountSubjectAppService, SsoSessionRepository sessionRepository) {
        this.clientRepository = clientRepository;
        this.clientSecretVerifier = clientSecretVerifier;
        this.codeStore = codeStore;
        this.tokenIssuer = tokenIssuer;
        this.accountSubjectAppService = accountSubjectAppService;
        this.sessionRepository = sessionRepository;
    }

    /**
     * 处理 /token（code grant / refresh grant）。
     *
     * @throws OidcException invalid_client / unsupported_grant_type / unauthorized_client / invalid_grant / invalid_request
     */
    public TokenResponse token(TokenRequest request) {
        SsoClient client = authenticateClient(request.clientId(), request.clientSecret());

        if (GRANT_AUTHORIZATION_CODE.equals(request.grantType())) {
            requireGrant(client, GRANT_AUTHORIZATION_CODE);
            return handleCodeGrant(client, request);
        }
        if (GRANT_REFRESH_TOKEN.equals(request.grantType())) {
            requireGrant(client, GRANT_REFRESH_TOKEN);
            return handleRefreshGrant(request);
        }
        throw new OidcException(SsoError.UNSUPPORTED_GRANT_TYPE, "不支持的 grant_type: " + request.grantType());
    }

    private TokenResponse handleCodeGrant(SsoClient client, TokenRequest request) {
        if (request.code() == null || request.code().isBlank()) {
            throw new OidcException(SsoError.INVALID_REQUEST, "code 缺失");
        }
        IssuedAuthorizationCode payload = codeStore.consume(request.code())
            .orElseThrow(() -> new OidcException(SsoError.INVALID_GRANT, "授权码无效或已使用"));
        // 验绑：code 必须属于该 client、且 redirect_uri 与发码时一致（防 code 挪用）
        if (!payload.clientId().equals(client.clientId())) {
            throw new OidcException(SsoError.INVALID_GRANT, "授权码不属于该 client");
        }
        if (request.redirectUri() == null || !request.redirectUri().equals(payload.redirectUri())) {
            throw new OidcException(SsoError.INVALID_GRANT, "redirect_uri 与授权时不一致");
        }

        SubjectView subject = loadSubject(payload.userId());
        ensureUsable(subject);
        // scope 透传进 access_token，供 /userinfo 按授权范围过滤返回资料（issue #17）；
        // sessionId 透传绑进 refresh（准 SLO，issue #19）。
        IssuedTokens issued = tokenIssuer.issue(subject, payload.nonce(), payload.scope(), payload.sessionId());
        return toResponse(issued);
    }

    private TokenResponse handleRefreshGrant(TokenRequest request) {
        if (request.refreshToken() == null || request.refreshToken().isBlank()) {
            throw new OidcException(SsoError.INVALID_REQUEST, "refresh_token 缺失");
        }
        RefreshTokenPayload payload = tokenIssuer.consumeRefresh(request.refreshToken())
            .orElseThrow(() -> new OidcException(SsoError.INVALID_GRANT, "refresh_token 无效或已使用"));
        // 准 SLO（issue #19）：refresh 绑 SSO 会话——会话已失效（登出/改密/封号/过期）则拒发，
        // access 15min 短命自然收尾。sessionId 为空 = 旧链路（#21 已删）签发的 refresh，一并拒发——
        // 它们无法通过踢人校验，留着就是永久后门。
        if (payload.sessionId() == null || sessionRepository.findActive(payload.sessionId()).isEmpty()) {
            throw new OidcException(SsoError.INVALID_GRANT, "SSO 会话已失效");
        }
        SubjectView subject = loadSubject(payload.userId());
        ensureUsable(subject);
        // refresh 不携带 nonce/scope；新 refresh 绑同一仍存活的 SSO 会话。
        IssuedTokens issued = tokenIssuer.issue(subject, null, null, payload.sessionId());
        return toResponse(issued);
    }

    /**
     * 账号停用/锁定则拒发 token——OIDC 形态（invalid_grant），而非内部领域异常。
     *
     * <p>身份已由 code/refresh 证明；账号在签发与换 token 之间被封禁时，换 token 失败。
     * 可用性由 {@link SubjectView#status()} 表达（ADR-0008：调用方自决 gate——token 路径判 usable 抛）。</p>
     */
    private void ensureUsable(SubjectView subject) {
        if (subject.status() != SubjectStatus.USABLE) {
            throw new OidcException(SsoError.INVALID_GRANT, "账号已停用或锁定");
        }
    }

    /**
     * 取 subject 读模型——code/refresh 指向的账号已删时，{@link AccountSubjectAppService#subjectClaims} 抛
     * {@link DomainException}，统一映射 invalid_grant（不引 account 域错误码——ADR-0007 跨上下文不走 domain 错误）。
     */
    private SubjectView loadSubject(Long userId) {
        try {
            return accountSubjectAppService.subjectClaims(userId);
        } catch (DomainException ex) {
            throw new OidcException(SsoError.INVALID_GRANT, "用户不存在");
        }
    }

    private SsoClient authenticateClient(String clientId, String clientSecret) {
        if (clientId == null || clientId.isBlank()) {
            throw new OidcException(SsoError.INVALID_CLIENT, "client_id 缺失");
        }
        SsoClient client = clientRepository.findByClientId(clientId)
            .orElseThrow(() -> new OidcException(SsoError.INVALID_CLIENT, "client_id 无效或未注册"));
        if (clientSecret == null || !clientSecretVerifier.matches(clientSecret, client.clientSecretHash())) {
            throw new OidcException(SsoError.INVALID_CLIENT, "client 认证失败");
        }
        return client;
    }

    private void requireGrant(SsoClient client, String grantType) {
        if (!client.supportsGrant(grantType)) {
            throw new OidcException(SsoError.UNAUTHORIZED_CLIENT, "client 未授权该 grant_type");
        }
    }

    private static TokenResponse toResponse(IssuedTokens issued) {
        return new TokenResponse(
            issued.accessToken(), issued.tokenType(), issued.expiresIn(),
            issued.refreshToken(), issued.idToken());
    }
}
