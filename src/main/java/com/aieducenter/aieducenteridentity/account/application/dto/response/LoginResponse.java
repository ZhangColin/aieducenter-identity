package com.aieducenter.aieducenteridentity.account.application.dto.response;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

import com.cartisan.security.authentication.TokenInfo;

/**
 * 登录响应——OIDC 形态的可扩展结构（ADR-0002 / studio bug#4 结构部分）。
 *
 * <p>Phase 0b：仅 {@link #accessToken}（Sa-Token 会话 token）有值；{@link #refreshToken} 与
 * {@link #idToken} 为占位字段（JWT 三件套的内容在 issue #4 / Phase 1 填充）。先把结构立稳，
 * 避免登录产物从「残缺单字段」到「三 token」时再改响应形状。</p>
 *
 * @param accessToken  访问令牌（当前=Sa-Token 会话 token；Phase 1 起改为 JWT）
 * @param refreshToken 刷新令牌（占位，Phase 1 填——不透明串服务端存）
 * @param idToken      身份令牌（占位，Phase 1 填——JWT）
 * @param tokenType    令牌类型（Bearer）
 * @param expiresIn    accessToken 有效期（秒）
 */
public record LoginResponse(
    String accessToken,
    String refreshToken,
    String idToken,
    String tokenType,
    long expiresIn
) {
    public LoginResponse {
        Objects.requireNonNull(accessToken, "accessToken must not be null");
        if (tokenType == null || tokenType.isBlank()) {
            tokenType = "Bearer";
        }
    }

    /**
     * 由 Sa-Token 会话 {@link TokenInfo} 构造登录响应。
     *
     * <p>Phase 0b：refreshToken / idToken 为占位（Phase 1 填），expiresIn 取自会话过期时间。</p>
     */
    public static LoginResponse fromSession(TokenInfo tokenInfo) {
        Objects.requireNonNull(tokenInfo, "tokenInfo must not be null");
        long expiresIn = Math.max(0, Duration.between(Instant.now(), tokenInfo.expireTime()).toSeconds());
        return new LoginResponse(tokenInfo.token(), null, null, "Bearer", expiresIn);
    }
}
