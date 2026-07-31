package com.aieducenter.aieducenteridentity.account.application;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.aieducenter.aieducenteridentity.account.application.dto.response.LoginResponse;
import com.aieducenter.aieducenteridentity.account.domain.aggregate.Account;
import com.aieducenter.aieducenteridentity.account.domain.aggregate.Profile;
import com.aieducenter.aieducenteridentity.account.domain.error.AccountError;
import com.aieducenter.aieducenteridentity.account.domain.repository.AccountRepository;
import com.aieducenter.aieducenteridentity.account.domain.repository.ProfileRepository;
import com.aieducenter.aieducenteridentity.account.domain.token.AccessTokenClaims;
import com.aieducenter.aieducenteridentity.account.domain.token.AccessTokenSigner;
import com.aieducenter.aieducenteridentity.account.domain.token.IdTokenClaims;
import com.aieducenter.aieducenteridentity.account.domain.token.IdTokenSigner;
import com.aieducenter.aieducenteridentity.account.domain.token.IdpSessionRegistrar;
import com.aieducenter.aieducenteridentity.account.domain.token.RefreshTokenStore;
import com.aieducenter.aieducenteridentity.account.infrastructure.token.JwtTokenProperties;
import com.cartisan.core.exception.DomainException;

/**
 * token 签发编排应用服务（ADR-0002 / issue #11 + #13）。
 *
 * <p>token 生命周期中枢：login / register / refresh 三处都走 {@link #issue}——签 access/id JWT、登记 Sa-Token 会话
 * （保留 bug#1）、并生成不透明 refresh_token 存 Redis。{@link #refresh} 凭 refresh_token 原子取删后重新 issue，
 * 实现一次性轮换。至此 access + refresh + id 三 token 齐全。</p>
 *
 * @since 0.1.0
 */
@Service
public class AccountTokenAppService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int REFRESH_TOKEN_BYTES = 32;

    private final AccessTokenSigner accessTokenSigner;
    private final IdTokenSigner idTokenSigner;
    private final IdpSessionRegistrar sessionRegistrar;
    private final RefreshTokenStore refreshStore;
    private final JwtTokenProperties properties;
    private final AccountRepository accountRepository;
    private final ProfileRepository profileRepository;

    public AccountTokenAppService(AccessTokenSigner accessTokenSigner, IdTokenSigner idTokenSigner,
            IdpSessionRegistrar sessionRegistrar, RefreshTokenStore refreshStore, JwtTokenProperties properties,
            AccountRepository accountRepository, ProfileRepository profileRepository) {
        this.accessTokenSigner = accessTokenSigner;
        this.idTokenSigner = idTokenSigner;
        this.sessionRegistrar = sessionRegistrar;
        this.refreshStore = refreshStore;
        this.properties = properties;
        this.accountRepository = accountRepository;
        this.profileRepository = profileRepository;
    }

    /**
     * 为已认证账号签发登录产物（access + id JWT + 不透明 refresh），并建立会话。
     *
     * <p>login / register / refresh 共用——每次签发都生成新 refresh 并存（refresh 场景下旧 refresh 已被 consume 删除，
     * 即轮换）。</p>
     *
     * @param account 已通过身份验证的账号
     * @param profile 账号个人资料（可空——取 nickname/avatar 进 id_token）
     * @return 登录响应（access + refresh + id 三 token）
     */
    public LoginResponse issue(Account account, Profile profile) {
        long accessTtl = properties.getAccessTtlSeconds();
        Instant iat = Instant.now();
        Instant exp = iat.plusSeconds(accessTtl);
        String userId = String.valueOf(account.getId());

        String accessJwt = accessTokenSigner.sign(new AccessTokenClaims(
            properties.getIssuer(), userId, properties.getAudiences(), iat, exp, newJti()));
        String idJwt = idTokenSigner.sign(buildIdClaims(account, profile, userId, iat, exp));

        // 以 access JWT 为会话 token 值建立 Sa-Token 会话（内部保留 bug#1 写 userName）
        sessionRegistrar.registerSession(account.getId(), displayNameOf(account, profile), accessJwt, accessTtl);

        // 不透明 refresh_token 服务端存（轮换用）
        String refreshToken = newRefreshToken();
        refreshStore.save(refreshToken, account.getId(), Duration.ofSeconds(properties.getRefreshTtlSeconds()));

        return LoginResponse.of(accessJwt, refreshToken, idJwt, accessTtl);
    }

    /**
     * 凭 refresh_token 换新 access + id + refresh（一次性轮换）。
     *
     * <p>consume 原子取删旧 refresh → 加载账号 → 检查可登录 → issue 新三件套（含新 refresh）。
     * 非法/已用/过期的 refresh → {@link AccountError#REFRESH_TOKEN_INVALID}。</p>
     *
     * @param refreshToken 不透明 refresh_token
     * @return 新登录响应（access + 新 refresh + id）
     */
    public LoginResponse refresh(String refreshToken) {
        Long userId = refreshStore.consume(refreshToken)
            .orElseThrow(() -> new DomainException(AccountError.REFRESH_TOKEN_INVALID));
        Account account = accountRepository.findById(userId)
            .orElseThrow(() -> new DomainException(AccountError.USER_NOT_FOUND));
        // 账号封号/锁定不续 token（身份已由 refresh 证明，告知原因不构成枚举）
        account.ensureLoginable();
        Profile profile = profileRepository.findById(userId).orElse(null);
        return issue(account, profile);
    }

    private IdTokenClaims buildIdClaims(Account account, Profile profile, String userId, Instant iat, Instant exp) {
        String email = account.getEmail();
        String phone = account.getPhone();
        String nickname = profile != null ? profile.getNickname() : null;
        String picture = profile != null ? profile.getAvatar() : null;
        // 联络方式注册时已当场验证；登录/续 token 即视为已验证。
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

    /** 不透明 refresh_token：32 字节 SecureRandom → base64url（无填充，约 43 字符）。 */
    private static String newRefreshToken() {
        byte[] bytes = new byte[REFRESH_TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
