package com.aieducenter.aieducenteridentity.sso.application;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.springframework.stereotype.Service;

import com.aieducenter.aieducenteridentity.sso.domain.client.SsoClient;
import com.aieducenter.aieducenteridentity.sso.domain.code.AuthorizationCodeStore;
import com.aieducenter.aieducenteridentity.sso.domain.code.IssuedAuthorizationCode;

/**
 * 授权码签发编排——{@code /authorize}（二次 SSO 免登）与 {@code /api/auth/login}（首次登录）共用。
 *
 * <p>发 code（绑 client/redirect_uri/user/nonce/scope）+ 构造 {@code redirect_uri?code=&state=} 回调地址。
 * 不引入 ticket/interactionId（CONTEXT 最简方案）。</p>
 *
 * @since 0.1.0
 */
@Service
public class AuthorizationCodeAppService {

    private final AuthorizationCodeStore codeStore;

    public AuthorizationCodeAppService(AuthorizationCodeStore codeStore) {
        this.codeStore = codeStore;
    }

    /**
     * 发 code 并构造回调地址。
     *
     * @param userId      已认证用户
     * @param client      消费方（code 绑其 clientId）
     * @param redirectUri 白名单回调地址
     * @param nonce       OIDC nonce（可空）
     * @param scope       授权范围（可空）
     * @param sessionId   发 code 时的 SSO sessionId（透传绑进 code，供 /token 签 refresh 时绑会话；可空）
     * @param state       CSRF 串（可空，回带）
     * @return {@code redirect_uri?code=&state=}（state 为空则不带）
     */
    public String issueCodeAndRedirect(Long userId, SsoClient client, String redirectUri,
            String nonce, String scope, String sessionId, String state) {
        String code = codeStore.issue(new IssuedAuthorizationCode(
            client.clientId(), redirectUri, userId, nonce, scope, sessionId));
        return appendQuery(redirectUri, "code", code, "state", state);
    }

    /** 拼查询参数（base 已含 ? 则用 &；value 为 null 的对跳过）。 */
    static String appendQuery(String base, String... keyValue) {
        StringBuilder sb = new StringBuilder(base);
        String separator = base.contains("?") ? "&" : "?";
        for (int i = 0; i + 1 < keyValue.length; i += 2) {
            String key = keyValue[i];
            String value = keyValue[i + 1];
            if (value == null) {
                continue;
            }
            sb.append(separator)
                .append(URLEncoder.encode(key, StandardCharsets.UTF_8))
                .append("=")
                .append(URLEncoder.encode(value, StandardCharsets.UTF_8));
            separator = "&";
        }
        return sb.toString();
    }
}
