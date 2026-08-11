package com.aieducenter.aieducenteridentity.sso.application;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.aieducenter.aieducenteridentity.account.application.dto.response.SubjectView;
import com.aieducenter.aieducenteridentity.sso.application.dto.response.IssuedTokens;
import com.aieducenter.aieducenteridentity.sso.domain.token.AccessTokenClaims;
import com.aieducenter.aieducenteridentity.sso.domain.token.AccessTokenSigner;
import com.aieducenter.aieducenteridentity.sso.domain.token.IdTokenClaims;
import com.aieducenter.aieducenteridentity.sso.domain.token.IdTokenSigner;
import com.aieducenter.aieducenteridentity.sso.domain.token.RefreshTokenPayload;
import com.aieducenter.aieducenteridentity.sso.domain.token.RefreshTokenStore;
import com.aieducenter.aieducenteridentity.sso.infrastructure.token.JwtTokenProperties;

/**
 * token 签发核心（ADR-0004：纯签发逻辑，无会话概念；ADR-0008：归属 sso）。
 *
 * <p>签 access/id JWT（RS256）+ 生成不透明 refresh_token 存 Redis——「签发三件套」的唯一真相源，
 * OIDC {@code /token} 端点（授权码 / refresh grant）专用。调用方负责消费旧 refresh（轮换）与绑 SSO 会话。</p>
 *
 * <p>纯 sso 组件——subject 数据经 {@link SubjectView}（account 应用层兜出的统一读模型，ADR-0007/0008）
 * 传入，<b>零 account domain 依赖</b>（不注入 account 仓储 / 不拿 account 聚合）。调用方（{@code SsoTokenAppService}）
 * 经 {@code AccountSubjectAppService.subjectClaims} 取 {@link SubjectView}，可用性自决 gate 后调本类签发。</p>
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
     * 为已认证 subject 签发三件套；OIDC {@code /token}（授权码流）传 nonce（写 id_token）+ scope（写 access_token）+
     * sessionId（绑 SSO 会话，准 SLO）。
     *
     * <p>scope 进 access_token，供 {@code /userinfo} 按授权范围过滤返回的 profile/email/phone 资料（issue #17）。
     * 非授权码流（refresh grant）传 null——access_token 不带 scope，{@code /userinfo} 仅返回 {@code sub}。
     * sessionId 绑进 refresh_token：登出/改密/封号清会话后，refresh grant 校验会话存活，否则失效（issue #19 准 SLO）。</p>
     *
     * <p>{@code email_verified}/{@code phone_number_verified} 沿用既有启发式 {@code (value != null)}——联络方式注册时
     * 已当场验证，登录/续 token 即视为已验证（CONTEXT 不变式；正本清源另开）。</p>
     *
     * @param subject   已认证 subject 读模型（含 userId/email/phone/nickname/avatar；status 不参与签发，调用方已 gate）
     * @param nonce     OIDC nonce（/authorize 透传；非授权码流传 null）
     * @param scope     授权范围（空格分隔串；授权码流来自 code 绑定，非授权码流传 null）
     * @param sessionId 签发 refresh 时绑定的 SSO sessionId（准 SLO）
     * @return 签发结果（access + refresh + id 三 token）
     */
    public IssuedTokens issue(SubjectView subject, String nonce, String scope, String sessionId) {
        long accessTtl = properties.getAccessTtlSeconds();
        Instant iat = Instant.now();
        Instant exp = iat.plusSeconds(accessTtl);
        String userId = String.valueOf(subject.userId());

        String accessJwt = accessTokenSigner.sign(new AccessTokenClaims(
            properties.getIssuer(), userId, properties.getAudiences(), iat, exp, newJti(), scope));
        String idJwt = idTokenSigner.sign(buildIdClaims(subject, userId, iat, exp, nonce));

        // 不透明 refresh_token 服务端存（轮换用）；绑 SSO sessionId（准 SLO：会话失效即失效，issue #19）。
        String refreshToken = newRefreshToken();
        refreshStore.save(refreshToken, subject.userId(), sessionId, Duration.ofSeconds(properties.getRefreshTtlSeconds()));

        return IssuedTokens.of(accessJwt, refreshToken, idJwt, accessTtl);
    }

    /**
     * 原子取删 refresh_token（GETDEL 语义，轮换防重放）——薄封装 {@link RefreshTokenStore}。
     *
     * <p>refresh grant 的前置步骤：命中返回载荷（userId + sessionId——OIDC {@code /token} 凭 sessionId 校验 SSO 会话
     * 仍存活，issue #19 准 SLO），同一 refresh 再 consume 返回 empty（已轮换/已用/过期）。
     * 非法 refresh 由 {@code /token} 映射 invalid_grant。</p>
     *
     * @return 命中返回载荷（userId + sessionId）；不存在/已用/过期返回 empty
     */
    public Optional<RefreshTokenPayload> consumeRefresh(String refreshToken) {
        return refreshStore.consume(refreshToken);
    }

    private IdTokenClaims buildIdClaims(SubjectView subject, String userId, Instant iat, Instant exp, String nonce) {
        String email = subject.email();
        String phone = subject.phone();
        // 联络方式注册时已当场验证；登录/续 token 即视为已验证。
        return new IdTokenClaims(
            properties.getIssuer(), userId, properties.getAudiences(), iat, exp, newJti(),
            email, email != null,
            phone, phone != null,
            subject.nickname(), subject.avatar(), nonce);
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
