package com.aieducenter.aieducenteridentity.account.application;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.aieducenter.aieducenteridentity.account.application.dto.response.LoginResponse;
import com.aieducenter.aieducenteridentity.account.domain.aggregate.Account;
import com.aieducenter.aieducenteridentity.account.domain.aggregate.Profile;
import com.aieducenter.aieducenteridentity.account.domain.token.AccessTokenClaims;
import com.aieducenter.aieducenteridentity.account.domain.token.AccessTokenSigner;
import com.aieducenter.aieducenteridentity.account.domain.token.IdTokenClaims;
import com.aieducenter.aieducenteridentity.account.domain.token.IdTokenSigner;
import com.aieducenter.aieducenteridentity.account.domain.token.RefreshTokenStore;
import com.aieducenter.aieducenteridentity.account.infrastructure.token.JwtTokenProperties;

/**
 * token 签发核心（ADR-0004：从 {@code AccountTokenAppService} 摘出的纯签发逻辑，无 sa-token）。
 *
 * <p>签 access/id JWT（RS256）+ 生成不透明 refresh_token 存 Redis。login / register / refresh / OIDC {@code /token}
 * 共用——它是「签发三件套」的唯一真相源。调用方负责消费旧 refresh（轮换）与建会话（SSO 会话或遗留 sa-token 会话）。</p>
 *
 * <p>历史：#11/#13 此逻辑内联在 {@code AccountTokenAppService}（兼建 sa-token 会话）；ADR-0004 重审后 identity 弃
 * sa-token，token 归 {@code /token} 端点，故摘出中性核心供新 SSO 链路复用，旧 {@code AccountTokenAppService}
 * 改为委托本类后再建 sa-token 会话（遗留端点，#21 删）。</p>
 *
 * @since 0.1.0
 */
@Service
public class TokenIssuerAppService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int REFRESH_TOKEN_BYTES = 32;

    private final AccessTokenSigner accessTokenSigner;
    private final IdTokenSigner idTokenSigner;
    private final RefreshTokenStore refreshStore;
    private final JwtTokenProperties properties;

    public TokenIssuerAppService(AccessTokenSigner accessTokenSigner, IdTokenSigner idTokenSigner,
            RefreshTokenStore refreshStore, JwtTokenProperties properties) {
        this.accessTokenSigner = accessTokenSigner;
        this.idTokenSigner = idTokenSigner;
        this.refreshStore = refreshStore;
        this.properties = properties;
    }

    /**
     * 为已认证账号签发 access + id JWT + 不透明 refresh（nonce 不回带，非授权码流）。
     *
     * @param account 已通过身份验证的账号
     * @param profile 账号个人资料（可空）
     * @return 登录响应（access + refresh + id 三 token）
     */
    public LoginResponse issue(Account account, Profile profile) {
        return issue(account, profile, null);
    }

    /**
     * 为已认证账号签发三件套；授权码流 {@code /token} 传 nonce 写入 id_token（OIDC 防重放）。
     *
     * @param account 已通过身份验证的账号
     * @param profile 账号个人资料（可空——取 nickname/avatar 进 id_token）
     * @param nonce   OIDC nonce（/authorize 透传；非授权码流传 null）
     * @return 登录响应（access + refresh + id 三 token）
     */
    public LoginResponse issue(Account account, Profile profile, String nonce) {
        long accessTtl = properties.getAccessTtlSeconds();
        Instant iat = Instant.now();
        Instant exp = iat.plusSeconds(accessTtl);
        String userId = String.valueOf(account.getId());

        String accessJwt = accessTokenSigner.sign(new AccessTokenClaims(
            properties.getIssuer(), userId, properties.getAudiences(), iat, exp, newJti()));
        String idJwt = idTokenSigner.sign(buildIdClaims(account, profile, userId, iat, exp, nonce));

        // 不透明 refresh_token 服务端存（轮换用）。
        // NOTE: 当前只绑 userId，未绑 SSO 会话；CONTEXT「refresh 绑 SSO 会话（过期即失效）」的绑定校验留 #19
        //（登出/改密踢人需按 userId 清会话 + refresh）。届时 RefreshTokenStore 需扩 sessionId 维度。
        String refreshToken = newRefreshToken();
        refreshStore.save(refreshToken, account.getId(), Duration.ofSeconds(properties.getRefreshTtlSeconds()));

        return LoginResponse.of(accessJwt, refreshToken, idJwt, accessTtl);
    }

    /**
     * 原子取删 refresh_token（GETDEL 语义，轮换防重放）——薄封装 {@link RefreshTokenStore}。
     *
     * <p>refresh grant 的前置步骤：命中返回 userId（调用方据此加载账号、签发新三件套），同一 refresh 再 consume
     * 返回 empty（已轮换/已用/过期）。非法 refresh 的错误映射由调用方决定（遗留端点抛 REFRESH_TOKEN_INVALID，
     * OIDC {@code /token} 抛 invalid_grant）。</p>
     *
     * @return 命中返回 userId；不存在/已用/过期返回 empty
     */
    public Optional<Long> consumeRefresh(String refreshToken) {
        return refreshStore.consume(refreshToken);
    }

    private IdTokenClaims buildIdClaims(Account account, Profile profile, String userId,
            Instant iat, Instant exp, String nonce) {
        String email = account.getEmail();
        String phone = account.getPhone();
        String nickname = profile != null ? profile.getNickname() : null;
        String picture = profile != null ? profile.getAvatar() : null;
        // 联络方式注册时已当场验证；登录/续 token 即视为已验证。
        return new IdTokenClaims(
            properties.getIssuer(), userId, properties.getAudiences(), iat, exp, newJti(),
            email, email != null,
            phone, phone != null,
            nickname, picture, nonce);
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
