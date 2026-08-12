-- ============================================================================
-- Account Context: account_operation_log 审计表（ADR-0010 / #68）
-- ============================================================================
-- 后台管理（admin-console）状态变更类操作的审计流水——管理 AppService 在每个状态变更动作
-- 同步 append 一行。operator 身份显式取自 RequestContext（callerAppName / userId / userName，
-- 即 BFF 转发的运营人员），「不借 Auditable」——故本表不带 created_at/updated_at/created_by/
-- updated_by/deleted 那套 JPA 审计 + 软删字段（日志数据：量大、无需恢复、不软删，见
-- 「限界上下文代码编写规范」§3.1）。occurred_at 在聚合工厂 record() 显式落值。
--
-- 语义提醒（ADR-0010）：operator_user_id/user_name = 运营人员，target_user_id = 被管的终端 Account。
-- operator_* 三列可空（系统触发 / RequestContext 未带 operator 时），target_user_id / op_type 恒非空。
-- ============================================================================

CREATE TABLE act_account_operation_log (
    -- 主键（TSID），append-only 流水
    id BIGINT PRIMARY KEY,

    -- operator：动作发起方（管理 BFF 的调用方 appName + 已登录运营人员）
    operator_caller   VARCHAR(100),              -- 调用方 app_code（如 admin-console），可空
    operator_user_id  BIGINT,                    -- 运营人员 userId（RequestContext），可空
    operator_user_name VARCHAR(100),             -- 运营人员名（RequestContext），可空

    -- target：被操作的终端 Account（= 显式 {userId} 路径参数）
    target_user_id    BIGINT NOT NULL,

    -- 动作
    op_type           INTEGER NOT NULL,          -- OperationType code（1=DISABLE，后续 ACTIVATE/UNLOCK/... 扩展）
    reason            VARCHAR(500),              -- 原因（disable 必填，由命令 DTO 兜；其它动作可空）

    -- 发生时间（聚合工厂 record() 显式落值，非 JPA Auditing）
    occurred_at       TIMESTAMP NOT NULL
);

COMMENT ON TABLE act_account_operation_log IS '账号操作审计流水（管理状态变更类操作同步 append）';
COMMENT ON COLUMN act_account_operation_log.operator_caller IS '动作发起方 app_code（admin-console 等，可空）';
COMMENT ON COLUMN act_account_operation_log.operator_user_id IS '运营人员 userId（RequestContext，非被管终端用户，可空）';
COMMENT ON COLUMN act_account_operation_log.operator_user_name IS '运营人员名（RequestContext，可空）';
COMMENT ON COLUMN act_account_operation_log.target_user_id IS '被操作的终端 Account userId（显式路径参数）';
COMMENT ON COLUMN act_account_operation_log.op_type IS '操作类型（OperationType code：1=DISABLE …）';
COMMENT ON COLUMN act_account_operation_log.reason IS '操作原因（disable 必填，其它可空）';
COMMENT ON COLUMN act_account_operation_log.occurred_at IS '发生时间（显式落值，非 JPA Auditing）';

-- 按被操作用户查审计历史（管理详情 / 审计端点）；按时间倒序排流水的辅助索引。
CREATE INDEX idx_act_account_operation_log_target ON act_account_operation_log(target_user_id);
CREATE INDEX idx_act_account_operation_log_occurred ON act_account_operation_log(occurred_at);
