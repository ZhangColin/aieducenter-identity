# 从 aieducenter-studio 迁移账号基线（Phase 0 指南）

> 用途：Phase 0 的开工清单——把 studio 里**账号相关**的可用代码搬到本服务、按本轮决策改造、修 4 个必修 bug。
> 前提：studio = 「前台消费应用」，本服务 = 「全平台身份基座」。**只搬账号相关，别把消费应用的业务逻辑带进来。**
> 相关决策：[ADR-0001](adr/0001-account-credential-model.md)（Account 模型）、[ADR-0002](adr/0002-token-format-jwt.md)（token=JWT）、[ADR-0003](adr/0003-sso-satoken-self-built-oidc.md)（Sa-Token 自搭 OIDC）、[CONTEXT.md](../CONTEXT.md)。

## 包名迁移
studio 用 `com.aieducenter.account` / `com.aieducenter.verification`；本服务基础包是 `com.aieducenter.aieducenteridentity`。搬过来改包名：

| studio | 本服务 |
|---|---|
| `com.aieducenter.account.*` | `com.aieducenter.aieducenteridentity.account.*` |
| `com.aieducenter.verification.*` | `com.aieducenter.aieducenteridentity.verification.*` |

---

## 1. 搬过来（复用 + 改造）

### 1.1 account 上下文 — 按 ADR-0001 拆分
studio 的 `User` 聚合（`act_users` 一张表：id/username/email/phone_number/password/nickname/avatar + 审计 + 软删）要按 **ADR-0001** 拆：

- **`account` 表**（用户主表）：`id`（=userId，TSID，作 SSO `sub`）+ `email`（唯一、可空）+ `phone`（唯一、可空）+ `password_hash`（**可空**——社交账号无密码）+ 状态字段（`status`、`locked`、`last_login_at`）+ 审计 + 软删。
- **`profile` 表**（扩展，1:1）：`user_id`（FK）+ `nickname` + `avatar` + 以后的可扩展个人资料。
- **`external_identities` 表**（新增，社交）：`id` + `provider` + `provider_uid`（微信用 unionid）+ `user_id` + `created_at`。

搬这些文件（改包名）：
- `account/domain/aggregate/User.java` → 拆成 `Account` 聚合 + `Profile` 聚合（或 Account 持 profile 关联）。
- `account/domain/repository/UserRepository.java` → `AccountRepository`；保留 `findByEmail` / `findByPhone`，**新增** `findByExternalIdentity(provider, providerUid)`（社交登录用）。
- `account/domain/error/UserError.java` → 原样搬（含**防用户枚举**：`ACCOUNT_NOT_FOUND` 返回 401 而非 404，这个不变量要保住）。
- 注册/登录/改密/查询应用服务 + 命令/响应 DTO + mapper + controller。

> **studio 的 `username` 字段（NOT NULL、登录键、正则 `^[a-zA-Z][a-zA-Z0-9_]{2,19}$`）：丢掉**——不搬，见下「小决策」。

### 1.2 密码端口（原样搬，干净的六边形）
- `account/domain/service/AccountPasswordEncoder.java`（`@Port`，`encode` / `matches`）
- `account/domain/service/AccountPasswordEncoderService.java`（领域服务封装）
- `account/infrastructure/security/BCryptAccountPasswordEncoderAdapter.java`（BCrypt 适配器）

这套直接搬、改包名即可，结构没问题。

### 1.3 verification 上下文（整个搬，替换桩）
studio 的 `verification/` 是独立限界上下文，整套搬：
- 图形验证码：`CaptchaAppService` + `CaptchaController` + `RedisCaptchaRepository` + `CaptchaGenerationService`。
- 短信/邮箱码：`VerificationCodeAppService` + `VerificationCodeController` + `RedisVerificationCodeRepository` + `VerificationCodeGenerationService`。
- 限流：邮箱/手机 purpose 锁 + IP 计数（`tryAcquireEmailLock` / `tryAcquirePhoneLock` / `checkAndIncrementIp`）—— Redis 原子操作，搬。
- 端口：`MessageSender`（发短信/邮件）+ `LogMessageSenderAdapter`（**桩，要替换**）。
- account 侧的调用端口：`CaptchaPort`/`CaptchaAdapter`、`VerificationCodePort`/`VerificationCodeAdapter`（account/infrastructure/verification）。

### 1.4 注册→事件模式
- `account/application/dto/event/UserRegisteredEvent.java` + 监听器。搬这个**模式**（注册成功发领域事件，下游消费——如钱包 grant）。事件本身搬，监听器按本服务实际下游重写。

### 1.5 V1 迁移脚本改造
studio `V1__create_users_table.sql` → 本服务第一个迁移脚本，按 1.1 拆成 `account` + `profile`（+ `external_identities` 新表）。保留软删的**部分唯一索引**（`WHERE deleted = FALSE`，防软删重复触发唯一冲突）。

---

## 2. 净新增（studio 没有，要建）
1. **社交登录（微信）**：`external_identities` 表 + OAuth 回调控制器 + state/PKCE + 绑定/解绑。详见 CONTEXT.md「社交登录」。
2. **OIDC 层（ADR-0003）**：`/authorize` `/token` `/userinfo` `/jwks` `/discovery` `/logout`，自签 JWT、维护 jwks。
3. **token 升级（ADR-0002）**：access + refresh + id_token（都按 JWT 决策）。
4. **`/me` 端点**：返回当前用户 profile（studio 缺，前端 fetch 会 404）。

---

## 3. 必修 bug（搬过来先修）

| # | bug | 位置 | 修法 |
|---|---|---|---|
| 1 | **登录后未填 SaSession** → `RequestContext` 恒 null | `AccountLoginAppService.java` `loginByPassword`/`loginBySms` 里只调了 `authenticationService.login(user.getId())`（约 :75、:100），没 `StpUtil.getSession().set(...)` | 登录后按 cartisan-security 约定填 SaSession（`userName`、`tenantId` 等 RequestContext 需要的字段） |
| 2 | **受保护接口没用 `@RequireAuth`** → 实际没强制鉴权 | `AccountController.java` 全文无 `@RequireAuth`（`logout` 等都没挡） | 受保护接口加 `@RequireAuth`（如 `logout`、未来的 `/me`、改密码） |
| 3 | **硬编码短信码 `"123456"`** | `VerificationCodeAppService.java:171` `code = "123456"; // TODO` | 删掉这行 + 替换 `LogMessageSenderAdapter` 接真实短信通道 |
| 4 | **`LoginResponse` 只有 `{token}`** | `LoginResponse.java` `record LoginResponse(String token)` | OIDC 化（`access_token` / `refresh_token` / `id_token` / `expires_in` 等，配合 ADR-0002） |

---

## 4. 别搬（studio 是消费应用）
- **`tenant` 上下文**（studio 的 `V2__create_tenants_table.sql` / `V3__init_tenants_data.sql` / `tenant/` 代码）—— **不搬**。本服务的组织/租户是 Phase 4，且按平台模型重新设计（成员关系 + owner/admin/member 治理角色 + `tenantId` 可空），不是照抄 studio 那套业务。
- **studio 任何消费应用业务逻辑**（studio 作为前台应用的自有业务）—— 一律不带进来。

---

## 5. 小决策（已拍）
- **`username`**：**丢掉登录键**——注册不要 username，email/phone 作登录定位、userId 作锚点；显示名走 `profile.nickname`。（studio 的 username NOT NULL + 正则不搬。）
- **`password_hash` 改可空**：是——社交账号无密码，可空。
- **schema 前缀**：沿用 `act_`（account）。

---

## 6. 建议 Phase 0 顺序
1. 搬 `verification` 上下文（独立、改包名即可跑）+ 替换 `LogMessageSenderAdapter`、删 `123456`（bug #3）。
2. 搬 `account`：拆 `User` → `account` + `profile`、建 `external_identities` 表、改 V1 脚本（bug #5 小决策先拍）。
3. 搬密码端口 + `UserError` + 注册/登录/改密应用服务。
4. 修 bug #1（填 SaSession）、#2（`@RequireAuth`）、#4（`LoginResponse` OIDC 化——先占位、token 升级在 Phase 1 完成）。
5. 跑通注册/登录/登出/改密的端到端（Phase 0 验收）。
