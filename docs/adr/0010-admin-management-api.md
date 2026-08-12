# 后台管理 API：admin-console 经签名服务（管理调用方白名单 + operator 经 RequestContext + 审计）

> **范围修订（2026-08-12，[ADR-0011](0011-no-admin-password-management.md)）**：本文原列的「清密码 / 强制改密」两项**已移出范围**——identity 后台不处理终端用户密码（#71 / #72 wontfix）。下文出现的「清密码」「强制改密」字样为立项时历史叙述，以 ADR-0011 为准。

平台后台（admin）要对终端用户 Account 做统一管理（封号/解封/解锁/清密码/强制改密/踢人/审计）。admin 后端（BFF）作为签名调用方消费 account bc 签名服务，admin 前端只对接自己的 BFF。本决策是 [ADR-0009](0009-signed-service-api-per-bc.md)（签名服务按 bc 暴露、签名即准信）在"管理操作"上的延伸——核心要解决"admin 凭什么能做别的签名调用方不能做的危险操作"，以及"本服务怎么知道是哪个运营人员操作的"。

**决定**：

1. **管理端点归 account bc 签名服务**（`/api/account/*` 下新增），不开 `/api/admin/*`——"admin"是调用方维度，不是端点分组维度，守住 ADR-0009"一视同仁"叙事。危险操作（封号/解锁/清密码/强制改密/踢人等）多数应用层已实现（`AccountStatusAppService` 等，含自动踢人），本期只差开签名端点 + 上 gate。
2. **危险操作加管理调用方白名单 gate（过渡 stopgap）**：在 `@RequireSignature` 之上加 `@RequireManagementCaller` 注解 + 切面，比对 `RequestContext.getCallerAppName() ∈ 配置白名单`（默认 `admin-console`，admin-bff 在 app-registry 的 app_code）。**这不是正式 per-app 权限模型**——正式 per-app 权限细化推后（#64）。成本约等于零（一注解 + 一切面 + 一行配置），但把"任何签名应用都能封号"的口子收住。
3. **operator 身份走现成 RequestContext 透传（零框架改动）**：operator = admin BFF 的已登录运营人员。admin BFF 用 cartisan-security（Sa-Token）认证 operator → `RequestContext.userId/userName` → `OpenApiClient` 出站自动带 `X-User-Id/X-User-Name` → identity 入站 `RequestContextFilter` 自动还原 → 审计从 `RequestContext.getUserId()/getUserName()` 取。前置：admin BFF 把 operator 填进 `RequestContext`——用 cartisan-security（Sa-Token）时 `SecurityFilter` 自动填；用其它认证时 admin 自己加 filter 填。两种情况 identity 与框架都**零改动**。（cartisan-boot 要改的唯一场景：operator 独立成 BFF 登录用户之外的一等概念——平台级框架决策，非本期、非方案②前置。）
4. **审计日志**：新建 `account_operation_log` 表，状态变更类管理操作同步写（`operatorId/operatorName/action/targetUserId/reason/timestamp`）。本期只写不查（查审计端点推后）。
5. **状态机暴露**：`disable`（封号，原因必填）+ `activate`（解封）+ `unlock`（解除系统自动锁定）。`lock`（临时锁定）不暴露给后台——它是风控/系统动作，后台只善后解锁。不定时解封。

**为什么 operator 不走自定义 `X-Operator-Id` 头 / 接口参数**：调查 cartisan-openapi 发现 `OpenApiClient.post/put/get` 无自定义头入参、`RequestContext` 是固定字段 record 无扩展位——自定义头需给框架补口子（issue + 等）。而 operator 本就是 BFF 的已登录用户，框架**已经在透传** `X-User-Id/X-User-Name`——消费现成机制优于新造。接口参数不具备全平台通用性（每个管理接口都要手动穿参），被否。

**语义提醒**（写此以免后人困惑）：identity 签名服务路径里 `RequestContext.userId` = **operator**（BFF 转发的运营人员），**不是**被管的终端 Account——Account 永远是显式 `{userId}` 路径参数。两者不冲突：identity 签名路径无"当前终端用户"概念（不发 token、不建会话，目标用户总是显式传入）。

**范围切割（本期不做，独立工单做满）**：登录/安全事件流水（登录历史，需在所有登录路径埋点 + 事件表）不在本期——它是正交的分析能力，单独排期做满（#63）；后台直接开号 / 注销·删除账号 / 批量操作 / 解绑社交账号（依赖 Phase 3 external_identities）作为后台管理二期 backlog（#65）。
