package com.aieducenter.aieducenteridentity.account.domain.repository;

import com.aieducenter.aieducenteridentity.account.domain.aggregate.AccountOperationLog;
import com.cartisan.core.stereotype.Port;
import com.cartisan.core.stereotype.PortType;
import com.cartisan.data.jpa.repository.BaseRepository;

/**
 * 账号操作审计流水仓储（{@code account_operation_log}，ADR-0010 / #68）。
 *
 * <p>append-only：仅写（管理 AppService 状态变更动作同步 append）、不软删、不带读过滤。本期只写不查
 * （查审计端点推后）；查询方法随审计查询端点排期再加。</p>
 *
 * @since 0.1.0
 */
@Port(PortType.REPOSITORY)
public interface AccountOperationLogRepository extends BaseRepository<AccountOperationLog, Long> {
}
