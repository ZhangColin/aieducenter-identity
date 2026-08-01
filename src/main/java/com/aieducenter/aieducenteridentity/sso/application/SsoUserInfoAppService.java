package com.aieducenter.aieducenteridentity.sso.application;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.aieducenter.aieducenteridentity.account.domain.aggregate.Account;
import com.aieducenter.aieducenteridentity.account.domain.aggregate.Profile;
import com.aieducenter.aieducenteridentity.account.domain.repository.AccountRepository;
import com.aieducenter.aieducenteridentity.account.domain.repository.ProfileRepository;
import com.aieducenter.aieducenteridentity.account.domain.token.AccessTokenVerifier;
import com.aieducenter.aieducenteridentity.account.domain.token.VerifiedAccessToken;
import com.aieducenter.aieducenteridentity.sso.application.dto.UserInfoResponse;
import com.aieducenter.aieducenteridentity.sso.domain.error.OidcException;
import com.aieducenter.aieducenteridentity.sso.domain.error.SsoError;

/**
 * {@code /userinfo} 应用服务（OIDC UserInfo Endpoint，issue #17）。
 *
 * <p>Bearer access token → 验签（{@link AccessTokenVerifier}）→ 按 scope 过滤返回 sub/profile/email/phone。
 * 缺失/非 Bearer/验签失败/用户不存在 → 401 {@code invalid_token}（RFC 6750）。</p>
 *
 * @since 0.1.0
 */
@Service
public class SsoUserInfoAppService {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String SCOPE_DELIMITER = " ";

    private final AccessTokenVerifier accessTokenVerifier;
    private final AccountRepository accountRepository;
    private final ProfileRepository profileRepository;

    public SsoUserInfoAppService(AccessTokenVerifier accessTokenVerifier, AccountRepository accountRepository,
            ProfileRepository profileRepository) {
        this.accessTokenVerifier = accessTokenVerifier;
        this.accountRepository = accountRepository;
        this.profileRepository = profileRepository;
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
        Account account = accountRepository.findById(userId)
            .orElseThrow(() -> new OidcException(SsoError.INVALID_TOKEN, "访问令牌无效或已过期"));
        Profile profile = profileRepository.findById(userId).orElse(null);
        Set<String> scopes = parseScopes(verified.scope());
        return buildResponse(account, profile, scopes);
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

    private static Set<String> parseScopes(String scope) {
        if (scope == null || scope.isBlank()) {
            return Set.of();
        }
        return new HashSet<>(Arrays.asList(scope.split(SCOPE_DELIMITER)));
    }

    private static UserInfoResponse buildResponse(Account account, Profile profile, Set<String> scopes) {
        // sub 恒返回（OIDC userinfo 的核心声明），不 gate 在 openid scope 上：refresh grant 签发的 access token
        // 当前不带 scope（绑 SSO 会话留 #19），严格 gate 会致其 /userinfo 拿不到 sub，故取 lenient。
        boolean profileScope = scopes.contains("profile");
        boolean emailScope = scopes.contains("email");
        boolean phoneScope = scopes.contains("phone");
        String email = account.getEmail();
        String phone = account.getPhone();
        // 联络方式注册时已当场验证（CONTEXT 不变式），present 即视为已验证——与 id_token 一致（见 TokenIssuerAppService）。
        return new UserInfoResponse(
            String.valueOf(account.getId()),
            profileScope && profile != null ? profile.getNickname() : null,
            profileScope && profile != null ? profile.getAvatar() : null,
            emailScope ? email : null,
            emailScope && email != null ? Boolean.TRUE : null,
            phoneScope ? phone : null,
            phoneScope && phone != null ? Boolean.TRUE : null);
    }
}
