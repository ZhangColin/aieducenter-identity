package com.aieducenter.aieducenteridentity.sso.application;

import org.springframework.stereotype.Service;

import com.aieducenter.aieducenteridentity.account.domain.aggregate.Account;
import com.aieducenter.aieducenteridentity.account.domain.aggregate.Profile;
import com.aieducenter.aieducenteridentity.account.domain.repository.ProfileRepository;
import com.aieducenter.aieducenteridentity.sso.application.dto.SsoLoginResult;
import com.aieducenter.aieducenteridentity.sso.domain.client.SsoClient;
import com.aieducenter.aieducenteridentity.sso.domain.session.SsoSession;
import com.aieducenter.aieducenteridentity.sso.domain.session.SsoSessionRepository;

/**
 * 登录闭环后半段——身份已证明（密码/验证码/社交任一凭据）后的统一收尾（CONTEXT 登录契约）。
 *
 * <p>建 SSO 会话（显示名取 Profile 昵称，注册新号尚无 Profile → 落联络方式）→ 发 code →
 * {@code redirect_uri?code&state}。login / register / login-code 三个入口共用，保证同一契约。</p>
 *
 * @since 0.1.0
 */
@Service
public class SsoLoginCompletionAppService {

    private final SsoSessionRepository sessionRepository;
    private final AuthorizationCodeAppService codeService;
    private final ProfileRepository profileRepository;

    public SsoLoginCompletionAppService(SsoSessionRepository sessionRepository,
            AuthorizationCodeAppService codeService, ProfileRepository profileRepository) {
        this.sessionRepository = sessionRepository;
        this.codeService = codeService;
        this.profileRepository = profileRepository;
    }

    /**
     * 建 SSO 会话 + 发 code。
     *
     * @param account     已认证账号（调用方已完成凭据校验与 recordLogin）
     * @param client      消费方（code 绑其 clientId）
     * @param redirectUri 白名单回调地址
     * @param nonce       OIDC nonce（可空）
     * @param scope       授权范围（可空，绑进 code）
     * @param state       CSRF 串（可空，回带）
     * @return sessionId + 回调地址
     */
    public SsoLoginResult completeLogin(Account account, SsoClient client, String redirectUri,
            String nonce, String scope, String state) {
        Profile profile = profileRepository.findById(account.getId()).orElse(null);
        String nickname = profile != null ? profile.getNickname() : null;
        SsoSession session = sessionRepository.create(account.getId(), account.displayLabel(nickname));
        String redirectUrl = codeService.issueCodeAndRedirect(
            account.getId(), client, redirectUri, nonce, scope, session.sessionId(), state);
        return new SsoLoginResult(session.sessionId(), redirectUrl);
    }
}
