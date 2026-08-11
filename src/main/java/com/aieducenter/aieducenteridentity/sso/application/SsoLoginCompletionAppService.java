package com.aieducenter.aieducenteridentity.sso.application;

import org.springframework.stereotype.Service;

import com.aieducenter.aieducenteridentity.account.application.dto.response.SubjectView;
import com.aieducenter.aieducenteridentity.sso.application.dto.SsoLoginResult;
import com.aieducenter.aieducenteridentity.sso.domain.client.SsoClient;
import com.aieducenter.aieducenteridentity.sso.domain.session.SsoSession;
import com.aieducenter.aieducenteridentity.sso.domain.session.SsoSessionRepository;

/**
 * 登录闭环后半段——身份已证明（密码/验证码/社交任一凭据）后的统一收尾（CONTEXT 登录契约）。
 *
 * <p>建 SSO 会话（显示名取 {@link SubjectView#displayLabel()}——昵称优先、否则落联络方式）→ 发 code →
 * {@code redirect_uri?code&state}。login / register / login-code 三个入口共用，保证同一契约。</p>
 *
 * <p>消费 {@link SubjectView}（account 应用层兜出的统一读模型），不再回读 account 聚合 / Profile——
 * Profile 装载已收在 {@code AccountAuthAppService} 投影出 {@code SubjectView} 时（ADR-0007）。</p>
 *
 * @since 0.1.0
 */
@Service
public class SsoLoginCompletionAppService {

    private final SsoSessionRepository sessionRepository;
    private final AuthorizationCodeAppService codeService;

    public SsoLoginCompletionAppService(SsoSessionRepository sessionRepository,
            AuthorizationCodeAppService codeService) {
        this.sessionRepository = sessionRepository;
        this.codeService = codeService;
    }

    /**
     * 建 SSO 会话 + 发 code。
     *
     * @param subject      已认证 subject 读模型（调用方经 {@code AccountAuthAppService} 取得，
     *                     凭据校验 / recordLogin / Profile 装载均已完成）
     * @param client       消费方（code 绑其 clientId）
     * @param redirectUri  白名单回调地址
     * @param nonce        OIDC nonce（可空）
     * @param scope        授权范围（可空，绑进 code）
     * @param state        CSRF 串（可空，回带）
     * @return sessionId + 回调地址
     */
    public SsoLoginResult completeLogin(SubjectView subject, SsoClient client, String redirectUri,
            String nonce, String scope, String state) {
        SsoSession session = sessionRepository.create(subject.userId(), subject.displayLabel());
        String redirectUrl = codeService.issueCodeAndRedirect(
            subject.userId(), client, redirectUri, nonce, scope, session.sessionId(), state);
        return new SsoLoginResult(session.sessionId(), redirectUrl);
    }
}
