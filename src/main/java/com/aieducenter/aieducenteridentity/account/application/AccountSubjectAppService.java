package com.aieducenter.aieducenteridentity.account.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aieducenter.aieducenteridentity.account.application.dto.response.SubjectView;
import com.aieducenter.aieducenteridentity.account.domain.aggregate.Account;
import com.aieducenter.aieducenteridentity.account.domain.aggregate.Profile;
import com.aieducenter.aieducenteridentity.account.domain.error.AccountError;
import com.aieducenter.aieducenteridentity.account.domain.repository.AccountRepository;
import com.aieducenter.aieducenteridentity.account.domain.repository.ProfileRepository;
import com.cartisan.core.exception.DomainException;

/**
 * Subject 数据读缝——account 上下文给 sso 的「取 subject 声明数据」契约（ADR-0007/0008）。
 *
 * <p>sso 签发 token / 返回 userinfo 需要已认证用户的身份数据（userId/email/phone/nickname/avatar/可用性），
 * 经 {@link #subjectClaims(Long)} 取统一读模型 {@link SubjectView}，不再直穿 account domain 读聚合。</p>
 *
 * <h3>不 gate</h3>
 * <p>本方法<b>不判可用性、不抛停用/锁定</b>——只把 {@link SubjectView#status()} 兜出，由调用方自决 gate：
 * token 路径判 usable 抛（{@code invalid_grant}）、userinfo 路径忽略（照返，保既有 {@code ensureUsable} vs
 * 不判的差异）。</p>
 *
 * @since 0.1.0
 */
@Service
public class AccountSubjectAppService {

    private final AccountRepository accountRepository;
    private final ProfileRepository profileRepository;

    public AccountSubjectAppService(AccountRepository accountRepository, ProfileRepository profileRepository) {
        this.accountRepository = accountRepository;
        this.profileRepository = profileRepository;
    }

    /**
     * 取 subject 声明数据（供 token 签发 / userinfo）。
     *
     * <p>读 account + profile（可空）投影成 {@link SubjectView}，<b>不 gate</b>可用性——status 兜出、
     * 调用方自决。账号不存在抛 {@link AccountError#USER_NOT_FOUND}（按登录态 / token sub 查询时的不一致兜底）。</p>
     *
     * @param userId 用户 ID（SSO sub）
     * @return subject 读模型（含可用性 status，调用方自决 gate）
     * @throws DomainException USER_NOT_FOUND（404，userId 无对应账号）
     */
    @Transactional(readOnly = true)
    public SubjectView subjectClaims(Long userId) {
        Account account = accountRepository.findById(userId)
            .orElseThrow(() -> new DomainException(AccountError.USER_NOT_FOUND));
        Profile profile = profileRepository.findById(userId).orElse(null);
        return SubjectView.of(account, profile);
    }
}
