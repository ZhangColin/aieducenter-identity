package com.aieducenter.aieducenteridentity.account.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aieducenter.aieducenteridentity.account.application.dto.response.AccountManagementView;
import com.aieducenter.aieducenteridentity.account.domain.aggregate.Account;
import com.aieducenter.aieducenteridentity.account.domain.aggregate.AccountOperationLog;
import com.aieducenter.aieducenteridentity.account.domain.aggregate.Profile;
import com.aieducenter.aieducenteridentity.account.domain.enums.OperationType;
import com.aieducenter.aieducenteridentity.account.domain.error.AccountError;
import com.aieducenter.aieducenteridentity.account.domain.repository.AccountOperationLogRepository;
import com.aieducenter.aieducenteridentity.account.domain.repository.AccountRepository;
import com.aieducenter.aieducenteridentity.account.domain.repository.ProfileRepository;
import com.cartisan.core.context.RequestContext;
import com.cartisan.core.exception.DomainException;

/**
 * 账号管理应用服务——后台管理（admin-console）经签名服务对终端用户 Account 的统一管理入口
 *（ADR-0010 / #66）。
 *
 * <p>管理端点归 account bc 签名服务（{@code /api/account/*}），在 {@code @RequireSignature} 之上
 * 加 {@code @RequireManagementCaller} 白名单 gate（过渡 stopgap，默认 admin-console）。
 * operator 身份取自 {@code RequestContext}（admin BFF 经 {@code X-User-Id/X-User-Name} 透传），
 * 状态变更操作同步 append 审计（{@code account_operation_log}，#68）。</p>
 *
 * <h3>状态变更编排（#68 起）</h3>
 * <p>复用既有应用服务原语（{@link AccountStatusAppService} 等含自动踢人）+ 同事务 append 审计——本服务作
 * 编排入口：动作主流程（封号/踢人/改密…）+ 审计落库同事务（审计失败回滚动作，强一致）。审计 operator
 * 取 {@code RequestContext.getCallerAppName()/getUserId()/getUserName()}（语义：operator = 运营人员，
 * 非 target 终端用户，见 ADR-0010）。</p>
 *
 * @since 0.1.0
 */
@Service
public class AccountManagementAppService {

    private final AccountRepository accountRepository;
    private final ProfileRepository profileRepository;
    private final AccountStatusAppService statusAppService;
    private final AccountOperationLogRepository operationLogRepository;

    public AccountManagementAppService(AccountRepository accountRepository, ProfileRepository profileRepository,
            AccountStatusAppService statusAppService, AccountOperationLogRepository operationLogRepository) {
        this.accountRepository = accountRepository;
        this.profileRepository = profileRepository;
        this.statusAppService = statusAppService;
        this.operationLogRepository = operationLogRepository;
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

    /**
     * 封号（停用账号）——复用 {@link AccountStatusAppService#disable(Long)}（状态置 DISABLED + 自动踢人：
     * 清该 userId 所有 SSO 会话），同事务 append 审计 op_type=DISABLE。
     *
     * <p>与状态变更原语的关系：原语（{@code AccountStatusAppService.disable}）只管「改状态 + 踢人」，
     * 非管理路径（如风控）也用、不带审计；本方法在其上叠「管理编排 + 审计」——审计 append 与封号的
     * <b>DB 写</b>同事务（审计落库失败则封号 DB 写一并回滚，二者在 DB 层原子一致）。但原语内的 SSO 踢人
     * （{@code revokeQuietly}）走 Redis、非事务且 best-effort（失败不回滚、准 SLO），其 Redis 副作用不随
     * DB 回滚——审计罕见的落库失败会留下「DB 未封号但会话已清」的瞬态（用户重登即可），这是「复用原语踢人」
     * 的既定取舍（spec 要求复用 {@code AccountStatusAppService.disable}）。</p>
     *
     * <p>{@code reason} 必填由命令 DTO（{@code @NotBlank}）兜；operator 取 {@code RequestContext}
     * （callerAppName / 运营人员 userId / userName），target = {@code userId}。</p>
     *
     * @param userId 目标用户 ID（被管的终端 Account，显式路径参数）
     * @param reason 封号原因（必填，落审计）
     * @throws DomainException USER_NOT_FOUND（404，userId 无对应账号）
     */
    @Transactional
    public void disable(Long userId, String reason) {
        statusAppService.disable(userId);
        appendOperationLog(userId, OperationType.DISABLE, reason);
    }

    /**
     * 同事务 append 一条审计流水——operator 取自 {@link RequestContext}。
     *
     * @param targetUserId 被操作的终端 Account
     * @param opType       操作类型
     * @param reason       原因（可空，视动作）
     */
    private void appendOperationLog(Long targetUserId, OperationType opType, String reason) {
        operationLogRepository.save(AccountOperationLog.record(
            RequestContext.getCallerAppName(),
            RequestContext.getUserId(),
            RequestContext.getUserName(),
            targetUserId,
            opType,
            reason));
    }
}
