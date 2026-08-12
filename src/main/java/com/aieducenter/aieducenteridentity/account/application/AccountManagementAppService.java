package com.aieducenter.aieducenteridentity.account.application;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aieducenter.aieducenteridentity.account.application.dto.query.AccountSearchQuery;
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
import com.cartisan.data.jpa.specification.ConditionSpecifications;
import com.cartisan.web.response.PageResponse;

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
 * 编排入口：动作主流程（封号/解封/解锁/踢人/改密…）+ 审计落库同事务（审计失败回滚动作，强一致）。审计 operator
 * 取 {@code RequestContext.getCallerAppName()/getUserId()/getUserName()}（语义：operator = 运营人员，
 * 非 target 终端用户，见 ADR-0010）。</p>
 *
 * <h3>动作清单</h3>
 * <ul>
 *   <li>{@code disable}（#68）：封号 + 自动踢人，reason 必填。</li>
 *   <li>{@code activate}（#69）：解封，不改会话，reason 可空。</li>
 *   <li>{@code unlock}（#69）：解锁，不改会话，reason 可空。</li>
 *   <li>{@code revokeSessions}（#69）：独立踢人，不改状态，reason 可空。</li>
 *   <li>{@code search}（#70）：用户搜索（分页多条件），只读、不审计。</li>
 * </ul>
 *
 * <h3>用户搜索（#70）</h3>
 * <p>首次启用 {@code BaseRepository} 自带的 {@code JpaSpecificationExecutor} + {@code findAll(Specification, Pageable)}：
 * 由 {@link ConditionSpecifications#fromAnnotation(AccountSearchQuery)} 把 {@link AccountSearchQuery}
 * 的 {@code @Condition} 字段拼成 AND 组合 Specification，再分页查。软删除自动过滤（Hibernate restriction
 * contributor，Specification 查询亦生效）。结果投影成 {@link AccountManagementView}（与单账号管理详情同口径），
 * profile 按 userId 批量补取（避免 N+1）。读操作不审计。</p>
 *
 * @since 0.1.0
 */
@Service
public class AccountManagementAppService {

    private final AccountRepository accountRepository;
    private final ProfileRepository profileRepository;
    private final AccountStatusAppService statusAppService;
    private final SsoSessionRevoker sessionRevoker;
    private final AccountOperationLogRepository operationLogRepository;

    public AccountManagementAppService(AccountRepository accountRepository, ProfileRepository profileRepository,
            AccountStatusAppService statusAppService, SsoSessionRevoker sessionRevoker,
            AccountOperationLogRepository operationLogRepository) {
        this.accountRepository = accountRepository;
        this.profileRepository = profileRepository;
        this.statusAppService = statusAppService;
        this.sessionRevoker = sessionRevoker;
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
     * 搜索账号（分页多条件）——后台管理视图（#70）。
     *
     * <p>由 {@link ConditionSpecifications#fromAnnotation(AccountSearchQuery)} 把 {@link AccountSearchQuery}
     * 的 {@code @Condition} 字段拼成 AND 组合 {@link Specification}，调 {@code findAll(spec, pageable)} 分页查
     *（首次启用 BaseRepository 自带的 JpaSpecificationExecutor + {@code findAll(Pageable)}，#70）。
     * 不传的字段自动跳过（不过滤），读操作不审计。软删除自动过滤。</p>
     *
     * <p>结果投影成 {@link AccountManagementView}（与 {@link #managementDetail} 同口径）；profile 按本页 userId
     * 批量补取（{@code findAllById}，避免逐条 N+1），缺 profile 的账号 nickname/avatar 为 null。
     * 无结果 / 分页越界 → 空页（Spring Data {@code findAll} 行为，不报错）。</p>
     *
     * @param query    多条件查询（null / 空串字段即不过滤，多字段 AND 组合）
     * @param pageable 分页（page 0-based / size；越界返回空页）
     * @return 分页管理视图（items / total / page（1-based）/ size）
     */
    @Transactional(readOnly = true)
    public PageResponse<AccountManagementView> search(AccountSearchQuery query, Pageable pageable) {
        Specification<Account> spec = ConditionSpecifications.fromAnnotation(query);
        Page<Account> page = accountRepository.findAll(spec, pageable);

        List<Long> userIds = page.getContent().stream().map(Account::getId).toList();
        Map<Long, Profile> profiles = userIds.isEmpty()
            ? Map.of()
            : profileRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(Profile::getUserId, profile -> profile));

        List<AccountManagementView> items = page.getContent().stream()
            .map(account -> AccountManagementView.of(account, profiles.get(account.getId())))
            .toList();

        return new PageResponse<>(items, page.getTotalElements(),
            pageable.getPageNumber() + 1, pageable.getPageSize());
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
     * 解封（激活账号）——复用 {@link AccountStatusAppService#activate(Long)}（状态置 ACTIVE，不影响会话：
     * 封号时已清，用户需重新登录），同事务 append 审计 op_type=ACTIVATE。
     *
     * <p>与封号对称：activate/unlock 不带踢人（封号/锁定时已清会话），仅改账号状态。{@code reason} 可空
     * （低危可逆动作，调用方按需附「申诉成功」等）；operator 取 {@code RequestContext}，target = {@code userId}。</p>
     *
     * @param userId 目标用户 ID（被管的终端 Account，显式路径参数）
     * @param reason 操作原因（可空，落审计）
     * @throws DomainException USER_NOT_FOUND（404，userId 无对应账号）
     */
    @Transactional
    public void activate(Long userId, String reason) {
        statusAppService.activate(userId);
        appendOperationLog(userId, OperationType.ACTIVATE, reason);
    }

    /**
     * 解锁——复用 {@link AccountStatusAppService#unlock(Long)}（locked 置 false，不影响会话），
     * 同事务 append 审计 op_type=UNLOCK。
     *
     * <p>解除系统自动锁定（风控/连续登录失败触发的 lock）；后台只善后解锁，{@code lock}（临时锁定）
     * 不暴露给后台（ADR-0010）。{@code reason} 可空；operator 取 {@code RequestContext}，target = {@code userId}。</p>
     *
     * @param userId 目标用户 ID（被管的终端 Account，显式路径参数）
     * @param reason 操作原因（可空，落审计）
     * @throws DomainException USER_NOT_FOUND（404，userId 无对应账号）
     */
    @Transactional
    public void unlock(Long userId, String reason) {
        statusAppService.unlock(userId);
        appendOperationLog(userId, OperationType.UNLOCK, reason);
    }

    /**
     * 独立踢人——复用 {@link SsoSessionRevoker#revokeQuietly(Long)}（清该 userId 所有 SSO 会话），
     * <b>不改账号状态</b>，同事务 append 审计 op_type=REVOKE_SESSIONS。
     *
     * <p>与封号内的自动踢人区别：封号是「改状态 + 附带踢人」，本方法是「只踢人、不动状态」——用于运营
     * 单独清退在线会话（如怀疑会话泄露）而不封号。原语 {@code revokeQuietly} 走 Redis、best-effort
     * （失败仅告警不抛），无在线会话时撤销 0 个亦正常返回。{@code reason} 可空；operator 取
     * {@code RequestContext}，target = {@code userId}。审计 append 走 DB 事务（与 Redis 踢人无事务联动，
     * 同 {@link #disable} 的既定取舍）。</p>
     *
     * @param userId 目标用户 ID（被管的终端 Account，显式路径参数）
     * @param reason 操作原因（可空，落审计）
     */
    @Transactional
    public void revokeSessions(Long userId, String reason) {
        sessionRevoker.revokeQuietly(userId);
        appendOperationLog(userId, OperationType.REVOKE_SESSIONS, reason);
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
