package com.aieducenter.aieducenteridentity.account.domain.aggregate;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import com.aieducenter.aieducenteridentity.account.domain.enums.OperationType;
import com.cartisan.core.domain.AggregateRoot;
import com.cartisan.core.stereotype.Aggregate;
import com.cartisan.data.jpa.id.TsidGenerator;

import lombok.Getter;

/**
 * 账号操作审计流水——后台管理（admin-console）状态变更类操作的 append-only 审计记录（ADR-0010 / #68）。
 *
 * <p>每个管理状态变更动作（disable / 后续解封 / 解锁 / 踢人 / 密码操作…）在管理 AppService 内
 * 同步 append 一行：{@code operator} 三列取自 {@code RequestContext}（BFF 转发的运营人员），
 * {@code targetUserId} = 被管的终端 Account（显式路径参数），{@code opType} / {@code reason} 由动作决定，
 * {@code occurredAt} 在 {@link #record} 显式落值。</p>
 *
 * <h3>不借 Auditable</h3>
 * <p>审计的 operator 身份<b>显式</b>取自 RequestContext（{@code operatorUserId/operatorName/operatorCaller}），
 * 不走 JPA Auditing（{@code created_by} 等）——后者是「系统记录谁改了行」，前者是「业务上谁发起了这次治理动作」，
 * 两者语义不同（发起人可能是经 BFF 透传的运营人员，非本服务进程）。且日志数据量大、无需恢复、不软删
 * （见「限界上下文代码编写规范」§3.1），故本聚合不继承 {@code AuditableSoftDeletable}，无
 * {@code created_at/updated_at/created_by/updated_by/deleted} 字段，{@code occurred_at} 自管。</p>
 *
 * <h3>语义提醒（ADR-0010）</h3>
 * <p>{@code operatorUserId/operatorName} = 运营人员，<b>不是</b>被管的终端 Account——后者恒为
 * {@code targetUserId}。</p>
 *
 * @since 0.1.0
 */
@Entity
@Table(name = "act_account_operation_log")
@Aggregate
public class AccountOperationLog implements AggregateRoot<AccountOperationLog, Long> {

    @Getter
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    /** 动作发起方 app_code（admin-console 等）；可空（系统触发 / 未带 caller）。 */
    @Getter
    @Column(name = "operator_caller", length = 100)
    private String operatorCaller;

    /** 运营人员 userId（RequestContext，非被管终端用户）；可空。 */
    @Getter
    @Column(name = "operator_user_id")
    private Long operatorUserId;

    /** 运营人员名（RequestContext）；可空。 */
    @Getter
    @Column(name = "operator_user_name", length = 100)
    private String operatorUserName;

    /** 被操作的终端 Account userId（显式路径参数）。 */
    @Getter
    @Column(name = "target_user_id", nullable = false)
    private Long targetUserId;

    /** 操作类型。 */
    @Getter
    @Column(name = "op_type", nullable = false)
    private OperationType opType;

    /** 操作原因（disable 必填——由命令 DTO 兜；其它动作可空）。 */
    @Getter
    @Column(name = "reason", length = 500)
    private String reason;

    /** 发生时间（{@link #record} 显式落值，非 JPA Auditing）。 */
    @Getter
    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    protected AccountOperationLog() {
        // JPA required
    }

    /**
     * 记一条审计流水。
     *
     * <p>{@code operatorCaller/operatorUserId/operatorName} 取自 {@code RequestContext}（管理 BFF 透传的
     * 运营人员 + 调用方 appName），均可空；{@code targetUserId} / {@code opType} 为业务必填（由调用方保证）；
     * {@code reason} 视动作而定（disable 必填，由命令 DTO 校验兜底）。{@code occurredAt} 落当前时刻。</p>
     *
     * @param operatorCaller  动作发起方 app_code（可空）
     * @param operatorUserId  运营人员 userId（可空）
     * @param operatorUserName 运营人员名（可空）
     * @param targetUserId    被操作的终端 Account userId
     * @param opType          操作类型
     * @param reason          操作原因（可空，disable 必填由 DTO 兜）
     * @return 审计流水实例（未持久化）
     */
    public static AccountOperationLog record(String operatorCaller, Long operatorUserId, String operatorUserName,
                                             Long targetUserId, OperationType opType, String reason) {
        AccountOperationLog log = new AccountOperationLog();
        log.operatorCaller = operatorCaller;
        log.operatorUserId = operatorUserId;
        log.operatorUserName = operatorUserName;
        log.targetUserId = targetUserId;
        log.opType = opType;
        log.reason = reason;
        log.occurredAt = LocalDateTime.now();
        return log;
    }

    /**
     * JPA 保存前生成 id（TSID）。
     */
    @PrePersist
    void prePersist() {
        if (id == null) {
            this.id = TsidGenerator.newInstance().generate();
        }
    }
}
