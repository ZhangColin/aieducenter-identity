package com.aieducenter.aieducenteridentity.account.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aieducenter.aieducenteridentity.account.domain.aggregate.Account;
import com.aieducenter.aieducenteridentity.account.domain.error.AccountError;
import com.aieducenter.aieducenteridentity.account.domain.repository.AccountRepository;
import com.cartisan.core.exception.DomainException;

/**
 * 终端账号状态治理应用服务——封号/锁定（issue #19）。
 *
 * <p>封号（{@code disable}）/锁定（{@code lock}）后按 userId 清所有 SSO 会话（踢人）：被踢用户的 access 15min
 * 短命自然收尾、refresh 绑会话失效（准 SLO）。HTTP 入口（机机/管理）等 admin/签名鉴权就位后另开 issue；
 * 本期暴露应用服务能力供调用与集成测试。</p>
 *
 * <p>注：运营人员（Operator）认证与角色在统一后台 admin，不在本服务；本类治理的是平台终端账号状态，
 * 非运营身份。</p>
 *
 * @since 0.1.0
 */
@Service
@Transactional
public class AccountStatusAppService {

    private final AccountRepository accountRepository;
    private final SsoSessionRevoker sessionRevoker;

    public AccountStatusAppService(AccountRepository accountRepository, SsoSessionRevoker sessionRevoker) {
        this.accountRepository = accountRepository;
        this.sessionRevoker = sessionRevoker;
    }

    /**
     * 停用账号（封号）——清该 userId 所有 SSO 会话（踢人）。
     *
     * @throws DomainException USER_NOT_FOUND
     */
    public void disable(Long userId) {
        Account account = loadAccount(userId);
        account.disable();
        accountRepository.save(account);
        sessionRevoker.revokeQuietly(userId);
    }

    /**
     * 锁定账号——清该 userId 所有 SSO 会话（踢人）。
     *
     * @throws DomainException USER_NOT_FOUND
     */
    public void lock(Long userId) {
        Account account = loadAccount(userId);
        account.lock();
        accountRepository.save(account);
        sessionRevoker.revokeQuietly(userId);
    }

    /**
     * 激活账号（解封）——不影响会话（封号时已清；用户需重新登录）。
     *
     * @throws DomainException USER_NOT_FOUND
     */
    public void activate(Long userId) {
        Account account = loadAccount(userId);
        account.activate();
        accountRepository.save(account);
    }

    /**
     * 解锁账号——不影响会话。
     *
     * @throws DomainException USER_NOT_FOUND
     */
    public void unlock(Long userId) {
        Account account = loadAccount(userId);
        account.unlock();
        accountRepository.save(account);
    }

    private Account loadAccount(Long userId) {
        return accountRepository.findById(userId)
            .orElseThrow(() -> new DomainException(AccountError.USER_NOT_FOUND));
    }
}
