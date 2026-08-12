package com.aieducenter.aieducenteridentity.account.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aieducenter.aieducenteridentity.account.application.dto.response.AccountManagementView;
import com.aieducenter.aieducenteridentity.account.domain.aggregate.Account;
import com.aieducenter.aieducenteridentity.account.domain.aggregate.Profile;
import com.aieducenter.aieducenteridentity.account.domain.error.AccountError;
import com.aieducenter.aieducenteridentity.account.domain.repository.AccountRepository;
import com.aieducenter.aieducenteridentity.account.domain.repository.ProfileRepository;
import com.cartisan.core.exception.DomainException;

/**
 * 账号管理应用服务——后台管理（admin-console）经签名服务对终端用户 Account 的统一管理入口
 *（ADR-0010 / #66）。
 *
 * <p>管理端点归 account bc 签名服务（{@code /api/account/*}），在 {@code @RequireSignature} 之上
 * 加 {@code @RequireManagementCaller} 白名单 gate（过渡 stopgap，默认 admin-console）。
 * operator 身份取自 {@code RequestContext}（admin BFF 经 {@code X-User-Id/X-User-Name} 透传），
 * 状态变更操作的审计见 #68（{@code account_operation_log}）。</p>
 *
 * <h3>本期范围（#67）</h3>
 * <p>仅管理详情读（{@link #managementDetail(Long)}）——打通 gate 管道的最简读端点（tracer bullet）。
 * 封号/解封/解锁/重置密码/清密码/强制改密/踢人/搜索随 #68-#70 落地，复用本服务为编排入口。</p>
 *
 * @since 0.1.0
 */
@Service
public class AccountManagementAppService {

    private final AccountRepository accountRepository;
    private final ProfileRepository profileRepository;

    public AccountManagementAppService(AccountRepository accountRepository, ProfileRepository profileRepository) {
        this.accountRepository = accountRepository;
        this.profileRepository = profileRepository;
    }

    /**
     * 取账号管理详情（status / locked / hasPassword + 资料）。
     *
     * <p>读 account + profile（可空）投影成 {@link AccountManagementView}——区别于面向终端应用的
     * {@code SubjectView}：分列 status/locked、暴露 hasPassword，供运营动手前了解全貌。
     * userId 无对应账号抛 {@link AccountError#USER_NOT_FOUND}（404）。</p>
     *
     * @param userId 目标用户 ID（显式路径参数，非 operator）
     * @return 管理详情读模型
     * @throws DomainException USER_NOT_FOUND（404，userId 无对应账号）
     */
    @Transactional(readOnly = true)
    public AccountManagementView managementDetail(Long userId) {
        Account account = accountRepository.findById(userId)
            .orElseThrow(() -> new DomainException(AccountError.USER_NOT_FOUND));
        Profile profile = profileRepository.findById(account.getId()).orElse(null);
        return AccountManagementView.of(account, profile);
    }
}
