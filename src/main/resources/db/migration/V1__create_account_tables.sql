-- ============================================================================
-- Account Context: account + profile 表（ADR-0001）
-- ============================================================================
-- 与 studio（act_users 单表 + username 登录键）的差异：
--   ① 丢掉 username 登录键——email/phone 作登录定位、userId(id) 作稳定锚点；
--   ② password_hash 可空——社交/纯验证码账号无密码；
--   ③ 拆出 profile 扩展表（1:1）——昵称/头像不塞主表；
--   ④ 唯一约束改「软删部分唯一索引」（WHERE deleted = FALSE），防软删触发唯一冲突。
-- external_identities（社交绑定）留给 Phase 3，本轮不建。
-- ============================================================================

-- ----------------------------------------------------------------------------
-- account：终端用户主表（身份本体 = id/userId）
-- ----------------------------------------------------------------------------
CREATE TABLE act_account (
    -- 主键 = userId（TSID），作 SSO sub，换邮箱/换手机不变
    id BIGINT PRIMARY KEY,

    -- 登录定位字段（可空、全局唯一，用于「找到是哪个用户」）
    email VARCHAR(255),
    phone VARCHAR(20),

    -- 凭据：密码 hash（可空——社交/纯验证码账号无密码）
    password_hash VARCHAR(255),

    -- 状态字段
    status INTEGER NOT NULL DEFAULT 1,      -- AccountStatus code：1=ACTIVE, 0=DISABLED
    locked BOOLEAN NOT NULL DEFAULT FALSE,  -- 是否锁定（登录被拒）
    last_login_at TIMESTAMP,                -- 最后登录时间

    -- 审计字段（JPA Auditing 自动填充）
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,

    -- 软删标记
    deleted BOOLEAN NOT NULL DEFAULT FALSE
);

COMMENT ON TABLE act_account IS '终端用户主表（平台身份本体）';
COMMENT ON COLUMN act_account.id IS '主键 = userId（TSID），稳定身份锚点，作 SSO sub';
COMMENT ON COLUMN act_account.email IS '登录邮箱（可空、全局唯一）';
COMMENT ON COLUMN act_account.phone IS '登录手机号（可空、全局唯一）';
COMMENT ON COLUMN act_account.password_hash IS 'BCrypt 密码 hash（可空——社交/纯验证码账号无密码）';
COMMENT ON COLUMN act_account.status IS '账号状态（1=ACTIVE, 0=DISABLED）';
COMMENT ON COLUMN act_account.locked IS '是否锁定（TRUE 时登录被拒）';
COMMENT ON COLUMN act_account.last_login_at IS '最后登录时间';
COMMENT ON COLUMN act_account.deleted IS '软删标记（FALSE=有效, TRUE=已删）';

-- ----------------------------------------------------------------------------
-- profile：个人信息扩展表（与 account 1:1）
-- ----------------------------------------------------------------------------
CREATE TABLE act_profile (
    -- 主键 = userId（与 act_account.id 1:1）
    user_id BIGINT PRIMARY KEY,

    nickname VARCHAR(50),
    avatar VARCHAR(512),

    -- 审计字段
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,

    -- 软删标记
    deleted BOOLEAN NOT NULL DEFAULT FALSE
);

COMMENT ON TABLE act_profile IS '个人信息扩展表（与 account 1:1）';
COMMENT ON COLUMN act_profile.user_id IS 'userId（= act_account.id，1:1）';
COMMENT ON COLUMN act_profile.nickname IS '昵称（显示名）';
COMMENT ON COLUMN act_profile.avatar IS '头像 URL';

-- ============================================================================
-- 软删部分唯一索引（WHERE deleted = FALSE）
-- ============================================================================
-- email/phone 全局唯一只在「未软删」记录间生效：软删某账号后，同邮箱/手机可重新注册，
-- 不会撞唯一约束。这也让 existsByEmail/existsByPhone 查重天然排除已删记录。
-- ============================================================================

CREATE UNIQUE INDEX uq_act_account_email ON act_account(email) WHERE deleted = FALSE;
CREATE UNIQUE INDEX uq_act_account_phone ON act_account(phone) WHERE deleted = FALSE;
