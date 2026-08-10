package com.aieducenter.aieducenteridentity.sso.application;

import org.springframework.stereotype.Service;

import com.aieducenter.aieducenteridentity.sso.application.dto.LogoutResult;
import com.aieducenter.aieducenteridentity.sso.domain.client.SsoClient;
import com.aieducenter.aieducenteridentity.sso.domain.client.SsoClientValidationService;
import com.aieducenter.aieducenteridentity.sso.domain.session.SsoSessionRepository;

/**
 * RP-initiated logout 应用服务（CONTEXT 登出「准 SLO」/ issue #19）。
 *
 * <p>清当前 SSO 会话（凭 cookie 的 sessionId）+ 解析 {@code post_logout_redirect_uri}（精确匹配白名单，
 * 校验失败抛 {@link com.aieducenter.aieducenteridentity.sso.domain.error.OidcException} 冒泡——由 Controller
 * 局部 handler 跳兜底页，ADR-0006）。始终清会话（即使 redirect 参数无效或无 cookie），仅跳转受白名单约束。</p>
 *
 * <p>准 SLO：不主动通知其它应用；refresh 绑 SSO 会话（{@code SsoTokenAppService} 校验）+ access 15min 短命自然收尾，
 * 实现「登出后 ≤15min 全失效」。改密/封号踢人复用 {@link SsoSessionRepository#deleteByUserId}。</p>
 *
 * @since 0.1.0
 */
@Service
public class SsoLogoutAppService {

    private final SsoSessionRepository sessionRepository;
    private final SsoClientValidationService clientValidation;

    public SsoLogoutAppService(SsoSessionRepository sessionRepository, SsoClientValidationService clientValidation) {
        this.sessionRepository = sessionRepository;
        this.clientValidation = clientValidation;
    }

    /**
     * 登出：清当前 SSO 会话，解析登出后跳转地址。
     *
     * @param sessionId             当前浏览器 SSO sessionId（可空——无 cookie/已登出）
     * @param clientId              消费方 client_id（校验 post_logout_redirect_uri 白名单用，可空）
     * @param postLogoutRedirectUri 登出后跳转地址（可空——空则不重定向）
     * @param state                 CSRF 串（原样回带到 redirect，可空）
     * @return 登出结果（redirectUrl 为 null 表示不重定向）
     */
    public LogoutResult logout(String sessionId, String clientId, String postLogoutRedirectUri, String state) {
        // 始终清当前 SSO 会话（登出优先；无 cookie/已失效亦无妨）
        if (sessionId != null && !sessionId.isBlank()) {
            sessionRepository.delete(sessionId);
        }
        return new LogoutResult(resolvePostLogoutRedirect(clientId, postLogoutRedirectUri, state));
    }

    /**
     * 解析 post_logout_redirect_uri：缺失 → 不重定向（返 null，Controller 返 200）；未登记 / 未知 client →
     * 抛 {@link com.aieducenter.aieducenteridentity.sso.domain.error.OidcException} 冒泡（由 Controller 局部
     * handler 接住跳兜底页，ADR-0006）。
     *
     * <p>走独立的 {@code postLogoutRedirectUris} 白名单（ADR-0005，不再复用 {@code redirectUris}）；
     * 登出已完成（{@code sessionRepository.delete} 已先于校验执行），仅跳转被拒改走兜底页。</p>
     */
    private String resolvePostLogoutRedirect(String clientId, String postLogoutRedirectUri, String state) {
        if (postLogoutRedirectUri == null || postLogoutRedirectUri.isBlank()) {
            return null;
        }
        // 校验失败（未知 client / post_logout 未登记）→ 抛 OidcException 冒泡，Controller 局部 handler 跳兜底页
        SsoClient client = clientValidation.requireActiveClient(clientId);
        clientValidation.requirePostLogoutRedirectUri(client, postLogoutRedirectUri);
        return AuthorizationCodeAppService.appendQuery(postLogoutRedirectUri, "state", state);
    }
}
