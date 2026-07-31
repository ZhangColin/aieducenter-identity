package com.aieducenter.aieducenteridentity.account.application;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.aieducenter.aieducenteridentity.account.application.dto.response.LoginResponse;
import com.aieducenter.aieducenteridentity.account.domain.aggregate.Account;
import com.aieducenter.aieducenteridentity.account.domain.aggregate.Profile;
import com.aieducenter.aieducenteridentity.account.domain.token.AccessTokenClaims;
import com.aieducenter.aieducenteridentity.account.domain.token.AccessTokenSigner;
import com.aieducenter.aieducenteridentity.account.domain.token.IdTokenClaims;
import com.aieducenter.aieducenteridentity.account.domain.token.IdTokenSigner;
import com.aieducenter.aieducenteridentity.account.domain.token.IdpSessionRegistrar;
import com.aieducenter.aieducenteridentity.account.infrastructure.token.JwtTokenProperties;

/**
 * token 签发编排应用服务（ADR-0002 / issue #11）。
 *
 * <p>构造 access/id 声明 → 签两枚 RS256-JWT → 以 access JWT 为值注册 Sa-Token 会话（保留 bug#1）→ 拼
 * {@link LoginResponse}。login / register 都调它，token 子系统集中在此，便于 #7/#8（OIDC {@code /token}）复用。
 * {@code refresh_token} 本片仍占位 null（#② 填）。</p>
 *
 * @since 0.1.0
 */
@Service
public class AccountTokenAppService {

    private final AccessTokenSigner accessTokenSigner;
    private final IdTokenSigner idTokenSigner;
    private final IdpSessionRegistrar sessionRegistrar;
    private final JwtTokenProperties properties;

    public AccountTokenAppService(AccessTokenSigner accessTokenSigner, IdTokenSigner idTokenSigner,
            IdpSessionRegistrar sessionRegistrar, JwtTokenProperties properties) {
        this.accessTokenSigner = accessTokenSigner;
        this.idTokenSigner = idTokenSigner;
        this.sessionRegistrar = sessionRegistrar;
        this.properties = properties;
    }

    /**
     * 为已认证账号签发登录产物（access_token + id_token，并建立会话）。
     *
     * @param account 已通过身份验证的账号
     * @param profile 账号个人资料（可空——取 nickname/avatar 进 id_token）
     * @return 登录响应（含 access_token + id_token）
     */
    public LoginResponse issue(Account account, Profile profile) {
        long ttl = properties.getAccessTtlSeconds();
        Instant iat = Instant.now();
        Instant exp = iat.plusSeconds(ttl);
        String userId = String.valueOf(account.getId());

        String accessJwt = accessTokenSigner.sign(new AccessTokenClaims(
            properties.getIssuer(), userId, properties.getAudiences(), iat, exp, newJti()));
        String idJwt = idTokenSigner.sign(buildIdClaims(account, profile, userId, iat, exp));

        // 以 access JWT 为会话 token 值建立 Sa-Token 会话（内部保留 bug#1 写 userName）
        sessionRegistrar.registerSession(account.getId(), displayNameOf(account, profile), accessJwt, ttl);

        return LoginResponse.of(accessJwt, idJwt, ttl);
    }

    private IdTokenClaims buildIdClaims(Account account, Profile profile, String userId, Instant iat, Instant exp) {
        String email = account.getEmail();
        String phone = account.getPhone();
        String nickname = profile != null ? profile.getNickname() : null;
        String picture = profile != null ? profile.getAvatar() : null;
        // 联络方式注册时已当场验证；登录即视为已验证。
        return new IdTokenClaims(
            properties.getIssuer(), userId, properties.getAudiences(), iat, exp, newJti(),
            email, email != null,
            phone, phone != null,
            nickname, picture);
    }

    private static String displayNameOf(Account account, Profile profile) {
        if (profile != null && profile.getNickname() != null && !profile.getNickname().isBlank()) {
            return profile.getNickname();
        }
        return account.getEmail() != null ? account.getEmail() : account.getPhone();
    }

    private static String newJti() {
        return UUID.randomUUID().toString();
    }
}
