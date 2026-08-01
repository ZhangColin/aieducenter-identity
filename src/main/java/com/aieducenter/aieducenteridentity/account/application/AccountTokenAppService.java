package com.aieducenter.aieducenteridentity.account.application;

import org.springframework.stereotype.Service;

import com.aieducenter.aieducenteridentity.account.application.dto.response.LoginResponse;
import com.aieducenter.aieducenteridentity.account.domain.aggregate.Account;
import com.aieducenter.aieducenteridentity.account.domain.aggregate.Profile;
import com.aieducenter.aieducenteridentity.account.domain.error.AccountError;
import com.aieducenter.aieducenteridentity.account.domain.repository.AccountRepository;
import com.aieducenter.aieducenteridentity.account.domain.repository.ProfileRepository;
import com.aieducenter.aieducenteridentity.account.domain.token.IdpSessionRegistrar;
import com.aieducenter.aieducenteridentity.account.domain.token.RefreshTokenPayload;
import com.cartisan.core.exception.DomainException;

/**
 * 遗留 token 编排应用服务（#11 / #13 / ADR-0004）。
 *
 * <p>给遗留 {@code /api/account/login} / {@code /api/account/refresh} 用：在 {@link TokenIssuerAppService} 签发的三件套基础上，
 * <b>额外</b>以 access JWT 为值登记 sa-token 会话（{@link IdpSessionRegistrar}），使遗留 {@code @RequireAuth}
 * 链路仍可访问受保护接口。ADR-0004 已定 identity 弃 sa-token，本类与遗留端点一并在 #21 删除；新 SSO 链路直接用
 * {@link TokenIssuerAppService}（不经会话登记）。</p>
 *
 * @since 0.1.0
 */
@Service
public class AccountTokenAppService {

    private final TokenIssuerAppService tokenIssuer;
    private final IdpSessionRegistrar sessionRegistrar;
    private final AccountRepository accountRepository;
    private final ProfileRepository profileRepository;

    public AccountTokenAppService(TokenIssuerAppService tokenIssuer, IdpSessionRegistrar sessionRegistrar,
            AccountRepository accountRepository, ProfileRepository profileRepository) {
        this.tokenIssuer = tokenIssuer;
        this.sessionRegistrar = sessionRegistrar;
        this.accountRepository = accountRepository;
        this.profileRepository = profileRepository;
    }

    /**
     * 为已认证账号签发登录产物（access + id JWT + 不透明 refresh），并登记 sa-token 会话（遗留）。
     *
     * <p>login / register / refresh 共用。</p>
     *
     * @param account 已通过身份验证的账号
     * @param profile 账号个人资料（可空）
     * @return 登录响应（access + refresh + id 三 token）
     */
    public LoginResponse issue(Account account, Profile profile) {
        LoginResponse response = tokenIssuer.issue(account, profile);
        // 遗留：以 access JWT 为会话 token 值建 sa-token 会话（bug#1 写 userName 供 RequestContext 读取）。
        String nickname = profile != null ? profile.getNickname() : null;
        sessionRegistrar.registerSession(account.getId(), account.displayLabel(nickname),
            response.accessToken(), response.expiresIn());
        return response;
    }

    /**
     * 凭 refresh_token 换新 access + id + refresh（一次性轮换）。
     *
     * <p>consume 原子取删旧 refresh → 加载账号 → 检查可登录 → issue 新三件套（含 sa-token 会话登记 + 新 refresh）。
     * 非法/已用/过期的 refresh → {@link AccountError#REFRESH_TOKEN_INVALID}。</p>
     *
     * @param refreshToken 不透明 refresh_token
     * @return 新登录响应（access + 新 refresh + id）
     */
    public LoginResponse refresh(String refreshToken) {
        Long userId = tokenIssuer.consumeRefresh(refreshToken)
            .map(RefreshTokenPayload::userId)
            .orElseThrow(() -> new DomainException(AccountError.REFRESH_TOKEN_INVALID));
        Account account = accountRepository.findById(userId)
            .orElseThrow(() -> new DomainException(AccountError.USER_NOT_FOUND));
        // 账号封号/锁定不续 token（身份已由 refresh 证明，告知原因不构成枚举）
        account.ensureLoginable();
        Profile profile = profileRepository.findById(userId).orElse(null);
        return issue(account, profile);
    }
}
