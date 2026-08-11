package com.aieducenter.aieducenteridentity.sso.application;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.aieducenter.aieducenteridentity.account.application.AccountSubjectAppService;
import com.aieducenter.aieducenteridentity.account.application.dto.response.SubjectView;
import com.aieducenter.aieducenteridentity.sso.application.dto.UserInfoResponse;
import com.aieducenter.aieducenteridentity.sso.domain.error.OidcException;
import com.aieducenter.aieducenteridentity.sso.domain.error.SsoError;
import com.aieducenter.aieducenteridentity.sso.domain.token.AccessTokenVerifier;
import com.aieducenter.aieducenteridentity.sso.domain.token.VerifiedAccessToken;
import com.cartisan.core.exception.DomainException;

/**
 * {@code /userinfo} 应用服务（OIDC UserInfo Endpoint，issue #17；ADR-0008：token 三件套归属 sso）。
 *
 * <p>Bearer access token → 验签（{@link AccessTokenVerifier}，sso 内）→ 按 scope 过滤返回 sub/profile/email/phone。
 * subject 数据经 {@link AccountSubjectAppService#subjectClaims} 取 {@link SubjectView}（ADR-0007：跨上下文只走应用层，
 * 不注入 account 仓储 / 不拿 account 聚合）。缺失/非 Bearer/验签失败/用户不存在 → 401 {@code invalid_token}（RFC 6750）。</p>
 *
 * <p><b>不 gate 可用性</b>——忽略 {@link SubjectView#status()}：userinfo 路径照返，保既有与 token 路径
 * （停用/锁定拒发）的差异（ADR-0008 红旗②）。</p>
 *
 * @since 0.1.0
 */
@Service
public class SsoUserInfoAppService {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String SCOPE_DELIMITER = " ";

    private final AccessTokenVerifier accessTokenVerifier;
    private final AccountSubjectAppService accountSubjectAppService;

    public SsoUserInfoAppService(AccessTokenVerifier accessTokenVerifier, AccountSubjectAppService accountSubjectAppService) {
        this.accessTokenVerifier = accessTokenVerifier;
        this.accountSubjectAppService = accountSubjectAppService;
    }

    /**
     * 处理 /userinfo。
     *
     * @param authorization {@code Authorization} 头（{@code Bearer <access_token>}，可空）
     * @return 按授权 scope 过滤的用户资料
     * @throws OidcException access token 缺失/无效/过期/用户不存在（401 invalid_token）
     */
    public UserInfoResponse userInfo(String authorization) {
        String token = extractBearer(authorization);
        VerifiedAccessToken verified = accessTokenVerifier.verify(token)
            .orElseThrow(() -> new OidcException(SsoError.INVALID_TOKEN, "访问令牌无效或已过期"));
        Long userId = parseSubject(verified.sub());
        SubjectView subject = loadSubject(userId);
        Set<String> scopes = parseScopes(verified.scope());
        return buildResponse(subject, scopes);
    }

    private static String extractBearer(String authorization) {
        if (authorization == null
            || !authorization.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            throw new OidcException(SsoError.INVALID_TOKEN, "缺失 Bearer 访问令牌");
        }
        return authorization.substring(BEARER_PREFIX.length()).trim();
    }

    private static Long parseSubject(String sub) {
        try {
            return Long.parseLong(sub);
        } catch (NumberFormatException ex) {
            throw new OidcException(SsoError.INVALID_TOKEN, "访问令牌无效或已过期");
        }
    }

    /**
     * 取 subject 读模型——access_token 指向的账号已删时，{@link AccountSubjectAppService#subjectClaims} 抛
     * {@link DomainException}，统一映射 invalid_token（不引 account 域错误码——ADR-0007 跨上下文不走 domain 错误）。
     * 不 gate status——userinfo 路径照返（保既有差异）。
     */
    private SubjectView loadSubject(Long userId) {
        try {
            return accountSubjectAppService.subjectClaims(userId);
        } catch (DomainException ex) {
            throw new OidcException(SsoError.INVALID_TOKEN, "访问令牌无效或已过期");
        }
    }

    private static Set<String> parseScopes(String scope) {
        if (scope == null || scope.isBlank()) {
            return Set.of();
        }
        return new HashSet<>(Arrays.asList(scope.split(SCOPE_DELIMITER)));
    }

    private static UserInfoResponse buildResponse(SubjectView subject, Set<String> scopes) {
        // sub 恒返回（OIDC userinfo 的核心声明），不 gate 在 openid scope 上：refresh grant 签发的 access token
        // 当前不带 scope（绑 SSO 会话留 #19），严格 gate 会致其 /userinfo 拿不到 sub，故取 lenient。
        boolean profileScope = scopes.contains("profile");
        boolean emailScope = scopes.contains("email");
        boolean phoneScope = scopes.contains("phone");
        String email = subject.email();
        String phone = subject.phone();
        // 联络方式注册时已当场验证（CONTEXT 不变式），present 即视为已验证——与 id_token 一致（见 TokenIssuerAppService）。
        return new UserInfoResponse(
            String.valueOf(subject.userId()),
            profileScope ? subject.nickname() : null,
            profileScope ? subject.avatar() : null,
            emailScope ? email : null,
            emailScope && email != null ? Boolean.TRUE : null,
            phoneScope ? phone : null,
            phoneScope && phone != null ? Boolean.TRUE : null);
    }
}
