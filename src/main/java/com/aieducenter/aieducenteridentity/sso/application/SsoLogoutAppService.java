package com.aieducenter.aieducenteridentity.sso.application;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.aieducenter.aieducenteridentity.account.domain.token.IdTokenDecoder;
import com.aieducenter.aieducenteridentity.sso.application.dto.LogoutResult;
import com.aieducenter.aieducenteridentity.sso.domain.client.SsoClient;
import com.aieducenter.aieducenteridentity.sso.domain.client.SsoClientValidationService;
import com.aieducenter.aieducenteridentity.sso.domain.session.SsoSessionRepository;

/**
 * RP-initiated logout 应用服务（CONTEXT 登出「准 SLO」/ issue #19，id_token_hint 解析 issue #45）。
 *
 * <p>清当前 SSO 会话（凭 cookie 的 sessionId）+ 解析 {@code post_logout_redirect_uri}（精确匹配白名单，
 * 校验失败抛 {@link com.aieducenter.aieducenteridentity.sso.domain.error.OidcException} 冒泡——由 Controller
 * 局部 handler 跳兜底页，ADR-0006）。始终清会话（即使 redirect 参数无效或无 cookie），仅跳转受白名单约束。</p>
 *
 * <p>会话定位优先级：① SSO cookie 的 sessionId（精确当前会话）；② cookie 缺失时，{@code id_token_hint} 解析出的
 * {@code sub}（=userId）兜底，按 userId 清该用户所有会话（复用 {@link SsoSessionRepository#deleteByUserId}）。
 * hint 缺失 / 无效 / 无 sub 不阻断登出主流程（OIDC：hint 是「提示」非「凭据」，spec #39 完整验签留后续 issue）。</p>
 *
 * <p>准 SLO：不主动通知其它应用；refresh 绑 SSO 会话（{@code SsoTokenAppService} 校验）+ access 15min 短命自然收尾，
 * 实现「登出后 ≤15min 全失效」。改密/封号踢人复用 {@link SsoSessionRepository#deleteByUserId}。</p>
 *
 * @since 0.1.0
 */
@Service
public class SsoLogoutAppService {

    private static final Logger log = LoggerFactory.getLogger(SsoLogoutAppService.class);

    private final SsoSessionRepository sessionRepository;
    private final SsoClientValidationService clientValidation;
    private final IdTokenDecoder idTokenDecoder;

    public SsoLogoutAppService(SsoSessionRepository sessionRepository, SsoClientValidationService clientValidation,
            IdTokenDecoder idTokenDecoder) {
        this.sessionRepository = sessionRepository;
        this.clientValidation = clientValidation;
        this.idTokenDecoder = idTokenDecoder;
    }

    /**
     * 登出：清当前 SSO 会话（cookie 优先、id_token_hint 兜底），解析登出后跳转地址。
     *
     * @param sessionId             当前浏览器 SSO sessionId（可空——无 cookie/已登出）
     * @param clientId              消费方 client_id（校验 post_logout_redirect_uri 白名单 + 审计用，可空）
     * @param postLogoutRedirectUri 登出后跳转地址（可空——空则不重定向）
     * @param state                 CSRF 串（原样回带到 redirect，可空）
     * @param idTokenHint           OIDC id_token_hint（可空——解码出 sub 用于审计 + cookie 缺失时兜底定位）
     * @return 登出结果（redirectUrl 为 null 表示不重定向）
     */
    public LogoutResult logout(String sessionId, String clientId, String postLogoutRedirectUri, String state,
            String idTokenHint) {
        Optional<String> hintSub = idTokenDecoder.decodeSubject(idTokenHint);
        String sessionSource = clearSession(sessionId, hintSub);
        if (hintSub.isPresent()) {
            // 带 hint 时入审计日志：sub（hint 解析，=userId）+ client_id（请求参数；id_token 无 client_id claim，aud 是固定平台值）
            log.info("[logout] id_token_hint sub={}, client_id={}, sessionSource={}",
                hintSub.get(), clientId, sessionSource);
        }
        return new LogoutResult(resolvePostLogoutRedirect(clientId, postLogoutRedirectUri, state));
    }

    /**
     * 清当前 SSO 会话：cookie 的 sessionId 优先（精确当前会话）；缺失则 id_token_hint 的 sub 兜底按 userId 清。
     *
     * @param sessionId SSO cookie 的 sessionId（可空）
     * @param hintSub   id_token_hint 解析出的 sub（可空）
     * @return 会话定位来源——{@code "cookie"} / {@code "hint"} / {@code "none"}（审计用）
     */
    private String clearSession(String sessionId, Optional<String> hintSub) {
        if (sessionId != null && !sessionId.isBlank()) {
            sessionRepository.delete(sessionId);
            return "cookie";
        }
        if (hintSub.isPresent()) {
            try {
                sessionRepository.deleteByUserId(Long.parseLong(hintSub.get()));
                return "hint";
            } catch (NumberFormatException ex) {
                // hint 的 sub 非数值（非本域 id_token 形态）→ 跳过兜底，登出主流程不受影响
                log.warn("[logout] id_token_hint sub 非数值，跳过兜底清会话 sub={}", hintSub.get());
            }
        }
        return "none";
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
