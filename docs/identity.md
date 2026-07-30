# 起步包 — aieducenter-identity（身份服务 / 平台 IdP）

> 用途：拿到这个项目走 matt/superpowers 开发时的**起步上下文**。本文是指针 + 复用 + 净新增 + 待决，**不是设计**。
> 角色：基础能力层 · 终端用户身份基座 / 平台集中式身份提供者(IdP)。被所有业务应用消费（直调用户接口 + SSO 接入）。无 UI（登录页在 identity-web）。

## 必读（按序）
- [architecture.md](../architecture.md) §6.1（用户域 / IdP / SSO，**已按 cluster 精修**：Operator 已移出、一套 SSO 域）、§3.2（分层规则）、§4（身份与租户上下文）
- [CONTEXT.md](../../CONTEXT.md)：用户域/身份服务、SSO、组织/租户、治理角色、对接契约
- [integration-flows.md](../integration-flows.md) §1（SSO 登录端到端时序）
- [map.md](../../.scratch/base-platform/map.md) 决策：集中 IdP+SSO、SSO 对外=OIDC、SSO 实现=BFF、统一登录架构、可选/透明多租户、组织治理角色 vs 业务角色、**Operator 归统一后台**、**一套 SSO 域**
- 待立 ADR（`docs/adr/`）：0001 集中 IdP+SSO、0003 统一登录架构、0008 可选多租户

## 底座（直接依赖）
- **cartisan-security**：token/鉴权/身份机器（`AuthenticationService` + `StpInterface` SPI + `@RequireAuth/@RequireRole/@RequirePermission` + `SecurityFilter`→`RequestContext`）。**不建用户表**——本服务自带 account 表 + 实现 `authenticate()` + `StpInterface`。注意：默认 loginType 是 `"login"`，本服务只 Account 一种身份，不用多 `StpLogic`。
- **cartisan-openapi**：服务间 HMAC 签名调用 + `RequestContext` 跨服务透传。
- **cartisan-boot**：DDD/CQRS（JPA 写 / jOOQ 读）+ `RequestContext`（userId/tenantId/callerAppId）+ TSID。构建需开 `--enable-preview`（ScopedValue）。

## 从 studio 迁移（账号基线，迁移时改包名/模块名）
| 复用 | studio 来源 |
|---|---|
| `User` 聚合 + `act_users` schema + `UserRepository` | `account/domain/aggregate/User.java`、`db/migration/V1__create_users_table.sql` |
| 密码端口 + BCrypt 适配器 | `AccountPasswordEncoder` + `infrastructure/security/BCryptAccountPasswordEncoderAdapter.java` |
| `UserError` 目录（含防用户枚举） | `account/domain/error/UserError.java` |
| 验证上下文（图形验证码 + 短信/邮箱码，Redis 限流、一次性） | `verification/` 整个上下文 |
| 注册→租户事件模式 | `UserRegisteredEvent` + 监听器 |

> studio 是「前台消费应用」，本服务是「全平台身份基座」——**只搬账号相关**，别把消费应用的业务逻辑带进来。

## 净新增（studio 没有，要建）
1. **SSO/OIDC 层**（cartisan-security **无** SSO）：`/authorize`（BFF，浏览器）+ `/token` `/userinfo` `/jwks` `/discovery`（纯 API）+ IdP 会话。逻辑两层、物理合一（同进程）。时序见 integration-flows.md §1。
2. **社交登录**（微信/Google/Apple，零基础）：`external_identities` 表（provider/provider_uid/unionid）+ OAuth 回调控制器 + state/PKCE + 绑定/解绑流程。
3. **token 升级**：studio 现是 opaque UUID、无 refresh；OIDC 需 access_token + refresh_token + id_token。
4. **`/me`（profile）端点**：studio 缺（前端 fetch 会 404）。
5. **组织·租户上下文**：成员关系（用户↔组织，多对多）+ 治理角色（owner/admin/member）。`tenantId` 全程可空（C 端无感）。
6. **写侧租户过滤**：cartisan-boot 缺（jOOQ 读侧有 helper，JPA 写侧无）——补 aspect 或 repository 约定。

## 必修 bug（从 studio 带过来，先修）
- 登录后**未填 SaSession**（`userName`/`tenantId`）→ `RequestContext` 恒 null。cartisan-security 约定：登录后 `StpUtil.getSession().set("userName", …)`。
- 受保护接口**没用 `@RequireAuth`** → 实际没强制鉴权。
- 硬编码短信码 `"123456"`（dev stub）→ 接真实短信通道。
- `LoginResponse` 只有 `{token}` → OIDC 要补全。

## 待决（留给本项目走流程拍）
- **SSO 实现选型（关键）**：① 在 Sa-Token 上自搭 OIDC 端点；② 加 `sa-token-sso` 模块（非标准 OIDC，但快）；③ 上 Spring Authorization Server（标准 OIDC，最重）。架构定的是「对外 OIDC 契约」，具体实现本项目评估（对上 map 雾区「Sa-Token OIDC 覆盖度待评估」）。
- token 用 JWT 还是「opaque + OIDC 封装」。
- Account 的 MFA 要不要做、做到哪一步。
- 注册开放 vs 邀请制；社交登录首批 provider。

## 边界（不做什么）
- 不做 Operator（在统一后台）。
- 不做应用内业务角色（应用自管，建在成员关系之上）。
- 不做商品/价格域。
- 财务（统一后台财务上下文）只读本服务，本服务不做财务。
