# aieducenter-identity — 限界上下文术语表

> 本服务 = 平台终端用户身份基座 / 集中式 IdP（基础能力层）。被所有业务应用消费（SSO 接入 + 机机用户数据 API）。无独立 UI（登录页在 identity-web）。
> **物理合一部署**：统一登录 BFF（`/authorize` + 登录闭环 + 种 SSO cookie）+ 用户域服务（`/token` `/userinfo` `/jwks` `/discovery`），一个进程、两层职责。
> 平台级术语（限界上下文 / IdP / SSO / 组织·租户 / 治理角色 / 对接契约…）见兄弟仓库 [../aieducenter-architecture/CONTEXT.md](../aieducenter-architecture/CONTEXT.md)，本文不重复，只记**本项目自身**演化出的术语与决策。

## 关键决策（2026-07-31 重审）

- **放弃 sa-token / cartisan-security 鉴权** — 见 [ADR-0004](docs/adr/0004-drop-satoken-single-sso-session.md)。cartisan-security 的甜区是「企业后台前端持 token 调自己后端」；identity 是 **IdP 不是消费应用**——三类接口（公开 / SSO 会话 / 机机签名 / OIDC 协议）没一类需要 sa-token 的 token-match 鉴权。原 [ADR-0003](docs/adr/0003-sso-satoken-self-built-oidc.md)「会话引擎继续 Sa-Token」被修订。
- **一套 SSO 会话**：identity 唯一用户会话 = IdP SSO 会话（cookie + Redis + 双超时）。不再有「access JWT 兼 Sa-Token 会话 token」那套。
- **token 是 OIDC 产物，归 `/token` 端点**：access/id/refresh 签给消费方 BFF，浏览器永不接触（BFF 模式）。login/register 不签 token 返回，只建会话发 code。
- **限界上下文只经 AppService 交往**（[ADR-0007](docs/adr/0007-cross-context-via-appservice.md)）：上下文之间只走应用层；sso→account 直接注入 account AppService，account→sso 逆流走 port 断环。token 三件套归属 sso、account 应用层=平台用户一体化缝（[ADR-0008](docs/adr/0008-token-ownership-sso-account-user-api.md)，落实 ADR-0004 决策3）。**适用边界**：ADR-0007 约束的是**跨 bc**（必须走被调方 AppService）；**同 bc 内多个 controller 共用本 bc AppService** 不受限（如 account 的签名 controller 与 sso 浏览器闭环 controller 都可跨 bc 调 AccountAppService）。
- **对外签名服务 API 独立一层（2026-08-12）** — account + verification 各自在 bc 下开 `@RequireSignature` 签名端点（`/api/account/*` `/api/verification-codes/*` `/api/captchas/*`），与 sso 浏览器闭环（`/api/sso/*`）、OIDC 协议端点（根）三层物理分明。签名即准信、不约束消费方用法（admin bff = 业务应用一视同仁）；不是特殊的"非 SSO 应用接入"。登录只回 `SubjectView`、不发 token。见 [ADR-0009](docs/adr/0009-signed-service-api-per-bc.md)。
- **后台管理 API（admin-console，2026-08-12）** — admin 后台经签名服务管理终端用户；危险操作加管理调用方白名单 gate（`admin-console`）；operator 走现成 `RequestContext` 透传、零框架改动；新建 `account_operation_log` 审计；登录历史等独立工单做满。见 [ADR-0010](docs/adr/0010-admin-management-api.md)。
- **后台不处理终端用户密码（2026-08-12）** — admin 侧不暴露任何用户密码操作（重置 / 清除 / 强制改密都不做，合规）；密码改动只走用户自助（验证码重置 / 验旧密改密）。#71 / #72 wontfix。见 [ADR-0011](docs/adr/0011-no-admin-password-management.md)（修订 ADR-0010 范围）。

## 继承的稳定不变式（平台已定，本项目遵守）

- Operator 不在本服务（运营认证 + 角色在统一后台 admin）。
- SSO 对外走 OIDC（`/authorize` `/token` `/userinfo` `/jwks` `/discovery` `/logout`）；**token 只存消费方服务端，浏览器不接触 token**（BFF 模式）。
- 应用不持有用户凭据（**scope = SSO 浏览器链路**：终端用户密码只在 identity-web 表单提交、应用前端/BFF 不经手明文密码；**不约束签名服务调用**——签名调用方传 password 验密是正常服务参数，见下方「签名服务 API」）；社交登录归本域统一接入、归一到中央 Account。
- `tenantId` 全程可空（C 端无感，无租户按 userId 隔离）；写侧需自带租户过滤。
- **应用管理/登记不在本服务**——identity 消费 app-registry 的 SsoClient，不自建 client 表。
- 管组织治理角色（owner/admin/member）；应用内业务角色归应用自管。
- ~~鉴权机器用 cartisan-security~~ → ADR-0004 修订：identity 不用 cartisan-security 鉴权。

## 术语表（本项目，随讨论沉淀）

### IdP SSO 会话（SSO cookie）—— 本服务唯一用户会话
浏览器侧 SSO cookie（`httpOnly` + `Secure` + `SameSite-Lax`），**不透明 sessionId**（不是 token）。Redis 存 `sessionId → {userId, 创建时间, 最后活跃时间}`，**闲置 30 天 / 绝对 90 天**（可配）。
- 消费者：`/authorize` 判免登（有 cookie 直接发 code）；identity-web 现凭 SSO cookie 调 register/login/login-code/client-info + 发码/captcha（**me / profile / 改密 / 重置 后端端点已就绪、identity-web 前端尚未接**，见个人中心 issue）。
- 登录/注册/社交成功时种 cookie（identity 后端，物理含统一登录 BFF）。
- 区别于 access/id/refresh token（OIDC 产物，给消费方 BFF，不进会话）。

### token（OIDC 产物，归 `/token` 端点）
- **access_token**：JWT(RS256)，15min，claims iss/sub/aud/iat/exp/jti。
- **id_token**：JWT(RS256)，标准 OIDC 声明 + nonce + 按 scope 的 email/phone/name/picture。
- **refresh_token**：opaque 串，Redis 存，**一次性轮换**，**绑 SSO 会话**（会话过期即失效）。
- 签发在 `/token`（code 换 token）；浏览器永不接触。

### 接口命名空间（按限界上下文分三层）

迁移后三层物理分明——OIDC 协议端点（规范钉死根路径，不动）/ sso bc 浏览器闭环（cookie + public，identity-web 用）/ account + verification bc 的签名服务（`@RequireSignature`，任何签名调用方）。**签名即准信**——调用方拿去做什么场景（给它自己的 UI 做登录/个人中心、做后台管理），不是本服务操心的；admin bff、业务应用、任何登记应用一视同仁。

| 层 / namespace | 鉴权 | 内容 | 谁调 |
|---|---|---|---|
| **OIDC 协议端点**（根；代码在 sso 包） | 协议各自（code / client_secret / bearer） | `/authorize` `/token` `/userinfo` `/jwks` `/.well-known/openid-configuration` `/logout` | OIDC 客户端 = SSO 消费方 BFF |
| **sso bc · 浏览器闭环** `/api/sso/*` | SSO cookie 或 public | login / login-code / register（都建 SSO 会话 + 发 code）/ client-info（公开）；me / profile / 改密 / 重置；captcha / verification-code | identity-web（浏览器） |
| **account bc · 签名服务** `/api/account/*` | `@RequireSignature` | authenticate / authenticate-by-code / register / reset-password / find / `{userId}` / `{userId}/profile` / `{userId}/change-password` | 任何签名调用方 |
| **verification bc · 签名服务** | `@RequireSignature` | `/api/verification-codes`（发码）/ `/api/verification-codes/verify`（验码）/ `/api/captchas`（图形码） | 任何签名调用方 |

> 迁移把 cookie/public 端点从 `/api/auth/*`、`/api/account/*`（旧 cookie 版）、`/api/captcha`、`/api/account/verification-code/*` 收拢到 `/api/sso/*`，**腾空 `/api/account/*` 给 account bc 的签名服务**；`VerificationCodeController` 原 path 寄生在 `/api/account`（包却在 verification bc）的历史错位一并修正。controller 归属按"调用方闭环"：SSO 浏览器闭环的 controller（cookie/public 鉴权）归 sso endpoints（即便操作 account/verification 数据，跨 bc 调对应 AppService）；签名服务 controller 归各自 bc。

### 签名服务 API（account + verification bc 对外暴露）

普通服务视角：identity 把 account + verification 的能力以**签名服务**方式对外暴露，**不是一个特殊的"非 SSO 应用接入"**。任何登记并持 apiKey/apiSecret 的调用方（admin bff、业务应用、SSO 应用做管理操作）签名后即可调，与调用方场景无关。

- **鉴权**：`@RequireSignature`（cartisan-openapi），框架 `RemoteApiKeyProvider` 调 `GET /api/app-registry/api-keys/{apiKey}` 解析调用方、Caffeine 30min 缓存；identity 的 `cartisan.openapi.apikey-service-url` 已配（#46），**零新基建**，对齐 payment/wechat 先例。调用方从 `RequestContext.getCallerAppName()` 取。
- **登录的产物 = `SubjectView`**（userId/email/phone/nickname/avatar/status），**不发 token**、不建 identity 侧会话。"登录态 / 登出 / session" 是调用方自己的概念，本服务不持有——故跨应用登出 / 会话一致性不在本服务职责内（调用方自管）。
- **凭据 scope**：签名调用方传 password 验密、传 code 验码，是正常服务参数，不受"应用不持有用户凭据"不变式约束（该不变式 scope = SSO 浏览器链路）。
- **操作分组**：按"要不要预知 userId"分——`authenticate` / `authenticate-by-code` / `register` / `reset-password` 按 `identifier`（应用持 email/phone 即可办）；`GET {userId}` / `PUT {userId}/profile` / `{userId}/change-password` 按 userId（应用已知用户）。
- **verification 作为服务**：发码 / 验码 / 图形码都开签名端点；不强制图形码（调用方自决）、不绑操作（裸验码也开，应用想先验后操作可以）；防轰炸靠限流（per apiKey + per target），防用户枚举不搬 SSO 浏览器链路那套（签名调用方可信，验码老实回 valid）。
- **错误响应**：标准 cartisan-web `ApiResponse` + 错误码 JSON，不复用 OIDC 的 `{error, error_description}`（那是 OIDC 协议端点的）。
- **per-app 操作权限**：本期"签名即准信 + 操作白名单"起步（默认签名应用可调全白名单、危险操作不暴露）；细粒度 per-app 权限推后整体设计。

**SSO 接入应用消费用户信息**：SSO 应用 = **OIDC 消费方 + 签名调用方双身份**——读**当前登录用户**基本信息走 `/userinfo`（OIDC 标准，BFF 持 access_token，scope 控制字段）；改 profile / 改密 / 读他人 / 批量管理走签名 `/api/account/*`（用其 apiKey）。非 SSO（纯签名）应用只有后者、无 token 概念。

### 后台管理 API（admin-console 经签名服务）

admin 后台（BFF = app_code `admin-console`）作为签名调用方消费 account bc 签名服务，对终端用户 Account 做统一管理。admin 前端只对接 admin BFF。

- **危险操作 gate（过渡）**：管理端点在 `@RequireSignature` 之上加 `@RequireManagementCaller` 注解 + 切面，比对 `callerAppName ∈ 配置白名单`（默认 `admin-console`）。**不是正式 per-app 权限模型**——正式 per-app 权限细化推后。本期管理能力（封号/解锁/踢人等；**不含任何密码相关操作**——见 [ADR-0011](docs/adr/0011-no-admin-password-management.md)）多数应用层已实现（`AccountStatusAppService` 等，含自动踢人），只差开签名端点 + 上 gate。
- **operator 身份 = admin BFF 已登录用户**：走现成 `RequestContext` 跨服务透传（`X-User-Id/X-User-Name`），identity 审计从 `RequestContext.getUserId()/getUserName()` 取，**零框架改动**。前置：admin BFF 把 operator 填进 `RequestContext`（cartisan-security 自动填，或 admin 自加 filter），identity 与框架都零改动。签名路径里 `RequestContext.userId` = operator，**非**被管 Account（Account 总是显式 `{userId}` 路径参数）。
- **审计**：`account_operation_log` 表，状态变更类管理操作同步写（operatorId/operatorName/action/targetUserId/reason/timestamp）。本期只写不查。
- **状态操作**：暴露 `disable`（封号，原因必填）/ `activate`（解封）/ `unlock`（解除系统锁定）；`lock`（临时锁定，风控/系统动作）不暴露；不定时解封。
- **范围切割**：登录/安全事件流水（登录历史）不在本期，独立工单做满；后台开号/注销删号/批量操作/解绑社交（依赖 Phase 3）= 后台二期 backlog。见 [ADR-0010](docs/adr/0010-admin-management-api.md)。

### 登录契约（/authorize 状态机 + /api/sso/login）
```
GET /authorize：验 client_id/redirect_uri/state → 看 SSO cookie
  有 cookie → 发 code, 302 回 redirect_uri?code&state          ← 二次 SSO
  无 cookie → 302 到登录页(透传 authorize 参数)
POST /api/sso/login {client_id, redirect_uri, state, nonce, scope, account, password}
  → 验凭据 → 建 SSO 会话 + 种 cookie → 发 code → 回 redirect_uri?code&state
POST /api/sso/login-code {同 authorize 透传 + account + code}
  → 验验证码(purpose=LOGIN) → 定位账号 → 建 SSO 会话 + 种 cookie → 发 code → 同上
POST /api/sso/register {同 authorize 透传 + email/phone + emailCode/phoneCode + password?}
  → 唯一性校验 → 当场验码(purpose=REGISTER) → 建号 → 注册即登录（同 login 后半段）
```
发 code 各处共用一个方法（login/register/login-code 经 SsoLoginCompletionAppService 统一后半段）；不引入 ticket/interactionId（最简方案）。scope 全链路透传（登录页 URL → login/login-code/register → 发码绑 code，#25）——首次登录与二次免登同参时 token scope/声明一致。
login/login-code/register 同时吃 **JSON 与 form-urlencoded**，**成功响应按提交方式分流**（#26）：
- **JSON 变体 = `200 {redirectUrl}`**——identity-web 登录/注册页是前后端分离 SPA：fetch POST JSON（同源，经 Next rewrite 代理），成功读体后 `window.location.href` 顶层导航回业务应用。不能回 302：fetch 会自动跟随、跨域跟随被 CORS 拦死（`redirect:'manual'` 也只拿到读不出 Location 的 opaqueredirect）。
- **form 变体 = `302 + Location`**——浏览器原生 form 顶层提交自然跟随（#23，无 JS 兜底保留）。
两变体同一 service、同一契约字段（camelCase：`clientId`/`redirectUri`/`state`/`nonce`/`scope`；form 只是编码差异，不是新契约——区别于 /authorize URL 参数的 snake_case）；Set-Cookie 种 SSO 会话两变体一致；失败响应（401/409/CODE_INVALID/400 `{error,error_description}`）两变体一致、不随提交方式变化。SSO 跨站链路不变式保持「跨站最后一跳 = 浏览器顶层导航、零跨域 fetch」（fetch 仅同源）。
验证码复用 verification 上下文：**purpose 分键**（REGISTER/LOGIN/RESET_PASSWORD，Redis key = 联络方式:用途），注册码与登录码互不串用；发码走 `/api/sso/verification-code/*` 公开端点带 purpose。login-code **防用户枚举**：「账号不存在」与「验证码错误」同一响应（错码由 verification 抛 CODE_INVALID；验码通过但账号不存在补抛同一 CODE_INVALID），停用/锁定在验码通过后才告知。

### Account（账号 / 终端用户）
平台一个终端用户。一张 `account` 表：`userId`（主键 TSID，作 SSO `sub`，换邮箱/手机不变）；登录定位字段 `email`/`phone`（唯一、可空）+ `password_hash`（可空）；状态字段。
> email/phone 是「登录入口 + 联络通道」，**不是身份本体**；身份本体是 userId。

### Subject（OIDC subject 读模型）
account 应用层对外兜出的「已认证身份数据」契约（`SubjectView`：userId / email / phone / nickname / avatar / status）。sso 经 `AccountAuthAppService`（authenticate / register）或 `AccountSubjectAppService`（subjectClaims）取得，用来造 OIDC 声明（token / userinfo）。sso 不直接碰 Account/Profile 聚合（ADR-0007/0008）。`status` 由调用方自决 gate（发 token 判 usable、userinfo 忽略）。

### Profile（个人信息）
昵称/头像等，扩展表（跟 account 1:1），不塞进用户主表。

### 凭据（Credential）
证明「我是这个 userId」的东西——密码(hash) / 邮箱手机验证码(Redis 一次性) / 第三方 token(不存)。登录 = 用任一凭据证明 → 解析 userId → 建 SSO 会话 + 发 code。

### 发码通道（MessageSender 端口，分期）
- 端口在 domain：`MessageSender.send(target, code, purpose)`；**当前唯一实现 = LogMessageSenderAdapter**（码进日志，生产占位）——真实短信/邮件网关**以后单独做**（独立排期），届时只换适配器，领域/契约零改动。
- 通道未接期间任何环境用户都收不到码；prod 注册/验证码登录实际不可用，可接受（prod 未上线）。

### dev 固定码（开发态验码基建，#28 配套，实现 = #29）
- **生成处固定**（不是验码处旁路）：非 prod 配 `verification.code.dev-code`（短信/邮箱码，如 246810）+ `verification.captcha.dev-code`（图形码，如 qa58）→ 生成器直接返回配置值，照常存 Redis、照常比对/一次性消费/过期/限流。被绕过的只有「通道投递」最后一公里，比对路径全真。
- **统一原则**：dev 里一切「答案在图片里/手机上/邮箱里」的环节生成处固定；识别与比对永不造假（不旁路、不 OCR——图形码干扰线生来反 OCR，喂自动化=脆弱测试）。
- **prod 防误开**：prod profile 配了任一键 → 启动拒绝（fail fast）。
- 定位 = **永久测试基建**（类比 Stripe test mode），真实通道接入后依然保留；比对逻辑另有本仓 CapturingMessageSender 集成测试每次构建担保。
- 各层自动化怎么拿答案：本仓 JVM 集成测试 = 直读 Redis 真码（CaptchaFlowIntegrationTest 模式）；跨进程 e2e/QA 脚本 = 敲配置固定值；人肉 QA = 无感（图形码图片画的就是固定串）。

### 图形验证码（Captcha）
- hutool `LineCaptcha`（130×40、4 字符、20 干扰线，base64 PNG），Redis 3min 一次性。
- **只保护短信发码**（防脚本轰炸烧钱，`captchaId`/`captchaCode` 必填）；邮箱发码不设（成本低，靠限流兜底）。
- dev 下生成处固定（`verification.captcha.dev-code`，见上节）；本仓集成测试直读 Redis 拿真码，不识别图片。
- 将来如升级行为验证码（滑块类 SaaS），随真实短信通道一起评估。

### External Identity（第三方绑定）
一张 `external_identities` 表：`provider` + `provider_uid` + `userId`。社交登录靠它定位/归一，一张表支持多 provider 绑同一用户。

### 注册
- 开放注册；**至少一联络方式**(email/phone 至少其一)**当场发码验证、验过才建号**（填了哪个联络方式就验哪个的码，未验的不落库——防占用他人联络方式）；**密码可选**（设了密码登，没设走 login-code 验证码登）。
- **注册即登录**：建号 → 建 SSO 会话 → 发 code（同登录后半段，不再单独登一次）。
- 仅社交可直接建号 + 引导补联络方式（不阻断）。
- 落地分期：**密码注册（#18）+ 当场验码/密码可选/验证码登录（#22）均已落地**（`/api/sso/register` + `/api/sso/login-code`；「至少一联络方式」与格式校验收在 `Account.register` 聚合不变量）。**#28 拍板（2026-08-02）：前端这期接验证码 UI**——identity-web Phase 1「无码注册」拍板推翻（#5 返工），email/phone 注册登录 + 图形码一次做全；dev 联调靠固定码，**不做缺码放行**，契约保持「码永远必填」。注册页密码字段 UI 这期**必填**（后端契约仍支持可选，「纯验证码登录用户」形态等有需求再放开）。

### 社交登录（微信扫码首批）
**两层 OAuth**：identity 对业务应用 = IdP，对微信 = 客户端。
```
点"微信登录" → identity 把 OIDC 上下文存 Redis(绑 ticket,微信 state=ticket) → 跳微信扫码
微信回调 /api/sso/social/wechat/callback?code&state=ticket → 凭 ticket 取回 OIDC 上下文
→ 微信 code 换 unionid → 查 external_identities(wechat, unionid):
     命中 → 该 userId
     未命中 → 建新 Account + 写 external_identities(引导补联络方式, 不阻断)
→ 建 SSO 会话 → 发 code → 302 回 redirect_uri?code&state
```
- 归一用 **unionid**（provider=wechat, provider_uid=unionid，微信开放平台下同主体多应用共享）。
- **本期只 PC 扫码**；移动端（公众号网页授权 / 小程序 login）以后按需。
- 绑定/解绑（已登录用户）归 `/api/account/*`。

### 登出（准 SLO）
- **RP-initiated logout**：应用发起跳 `GET /logout?client_id&post_logout_redirect_uri&state&id_token_hint` → 清 SSO 会话 + 清 cookie → 302 回 `post_logout_redirect_uri`（**校验独立的 postLogoutRedirectUris 白名单，不复用 redirect_uri**，[ADR-0005](docs/adr/0005-post-logout-redirect-uri-dedicated-allowlist.md)）；`id_token_hint` 接收解析做审计 / cookie 缺失兜底定位、不强制；校验失败时不再返 200 空白、跳 identity-web 兜底页（[ADR-0006](docs/adr/0006-error-response-split-by-caller.md)）。
- **准 SLO ≤15min**：不主动通知其它应用；靠 refresh 绑 SSO 会话失效 + access 15min 短命自然收尾。完整 back-channel SLO 排后。
- **改密/封号踢人**：按 userId 清所有 SSO 会话（同登出底层能力）。
- IdP 发起登出本期不做。

## 部署拓扑（URL → 进程 → cookie 域）

| URL | 打到哪个进程 | cookie 域 |
|---|---|---|
| `app.com/` | 业务前端（SPA 静态） | app.com |
| `app.com/api/*` · `app.com/auth/callback` | 业务 BFF | app.com |
| `identity.aieducenter.com/`（计划生产域名） | identity-web（SPA 静态） | identity.aieducenter.com |
| `identity.aieducenter.com/authorize` · `/api/*` | identity 后端 | identity.aieducenter.com（SSO cookie） |
| `identity.aieducenter.com/token` · `/userinfo` | identity 后端 | —（机机，不带 cookie） |

钉死三点：① `/authorize` 与 identity-web **同域**（SSO cookie 才带得上，分域即废）；② `/auth/callback` 是**业务 BFF 端点**（非前端）；③ `/token` **机机直连**（带 client_secret）。`SameSite=Lax`（扛跨站跳转）。

identity.aieducenter.com 是**计划生产域名**（DNS/证书未落地）：只活在文档与未来 prod 部署的 env 值里，**不进任何配置文件**（#74——issuer 是环境事实，配置只放已经为真的值）。

## 开发测试

- **本地**：`.localhost` 多域——浏览器自动解析到 127.0.0.1 + 当 secure context（免改 hosts、免证书）。端口：identity `identity.localhost:10001`、identity-web 登录页 `identity.localhost:10002`（Next dev，rewrite `/api/*`→:10001）、demo BFF `demo.localhost:10010`、demo-web `demo.localhost:3000`（Next 代理 `/api`·`/auth`→BFF）。一键起：`./dev-up.sh`（PG+Redis+四进程命令）；手册 `docs/guide/local-sso-debugging.md`。
- **环境 → issuer**（#74：环境绝对 URL 各环境显式配置，base/配置类零默认值，漏配启动即死）：
  - local：`http://identity.localhost:10001`（application-local.yml 实值，与 demo 消费方所配逐字符一致）
  - prod：env 驱动——`IDENTITY_TOKEN_ISSUER`（计划值 `https://identity.aieducenter.com`），缺失启动即死
  - dev：规划未落地（`identity.dev.aieducenter.com`），随部署立项建 profile、无历史包袱
- **demo 消费方**（仓内 `demo/`：`demo-backend` BFF + `demo-web`）：发起 /authorize + 收 callback + BFF 换 token（内存存、浏览器不接触）+ 显示用户。一身三任：**测试必需品 + 对接活示例 + 演示开发姿态**。
- ~~**dev 一键登**~~（#16/#27，**已移除**）：原为 identity-web 登录页缺席时的免密兜底；登录页已在、密码登录 local 无摩擦（无验证码 / 图形码），立项理由消失，随限界上下文重构（ADR-0008）整删（`DevLoginAppService` / `DevLoginController` / `DevAccountSeeder` + `identity.sso.dev-login.*` 配置）。local-dev 建号改走真注册 / SQL / 文档化 curl。
- **dev SSO 环境**（identity.dev.aieducenter.com）：真实 OIDC；redirect_uri 放行 `localhost:*`；预置测试账号 + 一键快速登录；发码通道 = Log（码进日志）；dev 固定码可配（guard 只拦字面 `prod` profile，启用前需部署侧先解决 profile 归属，见 #29 Rollout Notes）。**不做「指定 userId 直接发 token」捷径**。
- **消费方认证解耦**：业务代码只认「当前登录用户」抽象；姿态 A 连 dev SSO（主线）/ 姿态 B 本地 mock（兜底，消费方自写，不给 mock 端点）。

## 安全必需集

- `redirect_uri` **精确匹配**登录回调白名单（查 app-registry），不做前缀/通配；不匹配时**不重定向**、跳错误兜底页（防开放重定向）。
- `post_logout_redirect_uri` 同理精确匹配**独立的 postLogoutRedirectUris 白名单**（不复用 redirect_uri，[ADR-0005](docs/adr/0005-post-logout-redirect-uri-dedicated-allowlist.md)）；不匹配时不重定向。
- `code` **一次性 + 60s + 绑 client/redirect_uri**，换完即删。
- `state` 防 CSRF（消费方生成 + 回调比对）；`nonce` 防重放（id_token 回带）。
- PKCE **支持不强制**（BFF 机密客户端靠 client_secret）。
- `client_secret` **argon2 hash-only 比对**，永不返回明文。

## 错误处理（OIDC 标准）

- **按调用方分流**（[ADR-0006](docs/adr/0006-error-response-split-by-caller.md)）：浏览器导航类（`/authorize` `/logout`）出错 → 302 跳 identity-web 兜底页（后端纯 API、UI 全交 identity-web）；机机类（`/token` `/userinfo` `/jwks` `/discovery`）出错 → 维持 RFC6749 `{error, error_description}` JSON。
- `/authorize` 重定向前错（client_id/redirect_uri 无效或缺）→ **不重定向**，跳兜底页。
- `/authorize` 其它错 → 302 回 `redirect_uri?error&state`；用户取消 → `error=access_denied`。
- `/logout` 校验失败（post_logout_redirect_uri 未登记）→ 跳兜底页（提示已登出但未能自动返回），不再返 200 空白。
- infra 故障（app-registry 不可达抖动降级）→ `temporarily_unavailable`(503)，不混 `unauthorized_client`(400)。
- `/token` 错 → 400 + `invalid_grant` / `invalid_client`。
- 登录/注册失败（凭据错、验证码错、账号锁定）→ 留登录页显示，不回业务应用。

## 身份域术语速查（说人话）

- **IdP（身份提供者）**：「发身份证的机构」。本服务 = 平台 IdP。
- **SSO（单点登录）**：一次登录、所有接入应用都认。
- **OIDC**：SSO 的标准协议（端点 + token 格式）。
- **BFF**：应用自己的后端当前端浏览器的「代办」。SSO 里 token 存 BFF 服务端、浏览器只持 cookie。
- **SSO cookie**：IdP 种在浏览器的会话标识（不透明），`/authorize` 凭它判「这浏览器登录过没」。区别于 BFF 自己的业务 cookie。
- **token**：OIDC 三种——id_token(证明是谁, JWT) / access_token(调资源, JWT) / refresh_token(续 access, opaque)。浏览器永不接触。
- **JWT**：盖公章的介绍信，自带信息 + 签名，对接方用 IdP 公钥本地验。
- **opaque（不透明串）**：存包牌，本身不带信息，必须回 IdP 查。refresh_token 用它。
- **code**：一次性短命凭证，`/authorize` 发、`/token` 换 token。
- **state**：消费方生成的随机串，防 CSRF。
- **nonce**：消费方生成的随机串，写进 id_token 回带，防重放。
- **PKCE**：防 code 被截的「对暗号」机制；BFF 有 client_secret，用不上、不强制。
- **SLO（单点登出）**：一处登出、所有应用登出。本期做「准 SLO」≤15min。

## 跨项目集成：app-registry（#6-a #30 远程解析 + #6-b #31 服务间签名 + #6-c #32 容错，已落地）

`aieducenter-app-registry`（平台「应用/消费方」单一登记处，基础能力层）。identity 的 client 注册 = **消费**其 SsoClient facet，不自建 oauth_client 表。
- 契约：`GET /api/app-registry/sso-clients/{clientId}` → `SsoClientInfo{clientId, appId, clientName, clientSecretHash, redirectUris, scopes, grants, active}`（cartisan-web `ApiResponse` 包装，取 `.data`）。
- **验签（#6 拍板，#31 落地）**：bootstrap 端点标 `@RequireSignature`（app-registry 定型，理由=identity 平台核心服务可预持签名凭证、无死锁）。identity 出站走框架 `cartisan-openapi` 的 `OpenApiClient`——自动带服务间签名头（`X-Api-Key`/`X-Timestamp`/`X-Nonce`/`X-Body-Digest`/`X-Sign`，HMAC-SHA256）+ 跨服务 RequestContext 透传。`RemoteSsoClientRepositoryAdapter` 直接注入 `OpenApiClient`（**不另造 RestClient 拦截器**——出站签名是平台通用需求、跟其它服务一致）。签名凭证 `cartisan.openapi.self.api-key/api-secret` 配进 profile（prod 走 env 真凭据、local/test 占位）。
- **出站超时（#31）**：`OpenApiClient` 原 hardcode 10s/30s、调用方收窄不了；框架补 `cartisan.openapi.timeout.connect-seconds/read-seconds`（默认仍 10/30、零感知），identity 收窄到 3s/5s（bootstrap 在 SSO 登录热路径上）。
- **argon2 + BouncyCastle**：两端统一用 `spring-security-crypto` 的 `Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8()`（hash 自描述、两端互验）。该 encoder 运行时走 BouncyCastle，`spring-security-crypto` 将其声明为可选、Boot 3.4 BOM 不管理版本，故 identity 与 app-registry 都显式钉 `bcprov-jdk18on:1.78.1`（匹配 spring-security 6.4.x）。~~「argon2 不加 BouncyCastle」~~ 这句过时——encoder 离了 BC 会 NoClassDefFoundError。
- **组合状态由 app-registry 算好返回**：`active = client.active && app.active`；任一禁用 → 返回 `active=false` **且 `clientSecretHash=null`**（连 hash 都不给）。identity 拿到 null / active=false 自然拒办 SSO，**不二次查 app**。
- identity 侧：client 查询端口（`sso.domain.client`）→ **单一**远程适配器 `RemoteSsoClientRepositoryAdapter`（`sso.infrastructure.client`，#30 解析 / #31 签名 / #32 容错）。**无 stub、无 profile 分支**——dev/test/prod 同走远程；`StubSsoClientRepositoryAdapter` + `SsoProperties.stub-*` 配置 + `BCryptClientSecretVerifierAdapter` 已随 #30 删除；`Argon2ClientSecretVerifierAdapter` 替 BCrypt；app-registry `base-url` 配进 `SsoProperties.app-registry.base-url`（test 指向 WireMock）。
- **本地缓存（#30 基础 + #32 完整韧性）**：Caffeine 按 clientId 缓存 `SsoClientCacheEntry`（值 + 写入时刻），`maximumSize` 上限、**无时间淘汰**（last-known-good 尽量驻留以兜底）。① **fresh 窗口 30min**（写入起算）命中缓存不打远程——`/authorize` 高频查询不轰 app-registry；② **负面缓存**——`active=false` / `clientSecretHash=null` / **404「查不到」** 入缓存（`Optional.empty()`），禁用/不存在的 client 不反复打 app-registry；③ **抖动降级**——app-registry 抖动（5xx/连接失败/超时/其它非 404 错）时 **有过期缓存（哪怕 negative）就兜底**（值即最近一次成功解析、不返脏数据）、**无缓存就拒办**（抛 `temporarily_unavailable`(503)，[ADR-0006](docs/adr/0006-error-response-split-by-caller.md)：infra 故障 ≠ client 配置错；上层 `requireActiveClient`/`authenticateClient` 任其冒泡，错误码同为 `temporarily_unavailable`，但响应按调用方分流——`/authorize`（浏览器类）经 Controller 局部 `@ExceptionHandler` 跳 identity-web 兜底页（Location 带 `error=temporarily_unavailable`）、`/token` 等机机类由全局 `OidcExceptionHandler` 渲染 `{error:temporarily_unavailable}` 503 JSON），不抛 500。404 不视为抖动（是「查不到」的稳定结论，走负面缓存 → `unauthorized_client`/`invalid_client`）。fresh/stale 判定走注入的 `Clock`（测试 `MutableClock` 推进时间确定性触发过期）。
- secret 认证：`client_secret_post`，argon2 `matches()` 比对（hash-only，永不拿明文）。
- **应用管理/登记**（注册 app、加 SSO facet、填 redirect_uri、取 client_secret）在 app-registry / 统一后台，**不在本服务**。

来源：app-registry `CONTEXT.md` + ADR-0003(sso-facet) + 起步包 `docs/starters/app-registry.md`。

## 决策记录
见 [docs/adr/](docs/adr/)：
- [ADR-0001](docs/adr/0001-account-credential-model.md) Account 锚点与凭据模型
- [ADR-0002](docs/adr/0002-token-format-jwt.md) token = JWT
- [ADR-0003](docs/adr/0003-sso-satoken-self-built-oidc.md) SSO = Sa-Token 自搭 OIDC（**「会话引擎继续 Sa-Token」被 ADR-0004 修订**）
- [ADR-0004](docs/adr/0004-drop-satoken-single-sso-session.md) **弃 sa-token / 一套 SSO 会话 / token 归 /token**
- [ADR-0005](docs/adr/0005-post-logout-redirect-uri-dedicated-allowlist.md) post_logout_redirect_uri 独立白名单（不复用 redirect_uri）
- [ADR-0006](docs/adr/0006-error-response-split-by-caller.md) 端点错误响应按调用方分流（浏览器类跳 identity-web 兜底页 / 机机类 RFC6749 JSON）
- [ADR-0007](docs/adr/0007-cross-context-via-appservice.md) 跨上下文调用只走被调方 AppService（服务级 DDD 原则）
- [ADR-0008](docs/adr/0008-token-ownership-sso-account-user-api.md) token 三件套归属 sso + account 应用层=平台用户一体化缝
- [ADR-0009](docs/adr/0009-signed-service-api-per-bc.md) 对外签名服务 API 独立一层（按 bc 暴露、签名即准信、登录只回 SubjectView）
- [ADR-0010](docs/adr/0010-admin-management-api.md) 后台管理 API（admin-console 经签名服务 + 管理调用方白名单 + operator 经 RequestContext + 审计日志）
- [ADR-0011](docs/adr/0011-no-admin-password-management.md) 后台不处理终端用户密码（修订 ADR-0010 范围；#71/#72 wontfix）

## 构建分期（重排；旧 issue #5 等及 token-in-login 设计废弃）

- **Phase 1（SSO 会话基建 + 登录闭环）**：SSO 会话（cookie + Redis + 双超时）+ `/authorize` + 登录契约（`/api/sso/login`）+ 接口命名空间重组 + demo 消费方 + dev 环境。
- **Phase 2（OIDC 协议端点）**：`/token` + `/userinfo` + `/jwks` + `/discovery`。
- **Phase 3（注册 + 社交）**：注册流 + 微信扫码（PC）。
- **Phase 4（登出）**：RP-initiated logout + 准 SLO + 改密/封号踢人。
- **Phase 5（组织/租户，推后）**：成员关系 + 治理角色 + 写侧租户过滤。

## 待决议题

- [x] sa-token 去留 = **弃**（ADR-0004）
- [x] SSO 会话 = **一套 cookie 会话**；token 归 `/token`
- [x] 登录契约 = `/authorize` 状态机 + `/api/sso/login` 最简（无 ticket）
- [x] 注册 = 开放 + 至少一联络方式当场验证 + 密码可选 + 注册即登录
- [x] 社交 = 两层 OAuth + unionid 归一 + 本期只 PC 扫码
- [x] 登出 = 准 SLO（≤15min）+ RP-initiated；完整 back-channel SLO 排后
- [x] 开发测试 = `.localhost` + demo 消费方 + dev SSO + 消费方认证解耦
- [x] 安全集 + 错误框架（OIDC 标准）
- [x] post_logout_redirect_uri = 独立白名单、不复用 redirect_uri（ADR-0005）
- [x] id_token_hint = 接收解析做审计 / cookie 缺失兜底定位、不强制（不做完整免确认）
- [x] discovery 补 end_session_endpoint + front/back-channel_logout_supported + post_logout_redirect_uris_supported
- [x] 端点错误响应 = 按调用方分流（ADR-0006）
- [x] 应用管理/登记 = 不在本服务，消费 app-registry
- [x] #28 契约冲突 = 前端接码 UI（c-revised：email/phone 注册登录 + 图形码一次做全）+ dev 固定码配套；不做缺码放行
- [x] 验证码通道 = 分期：接口先行 + Log 占位 + dev 固定码（生成处固定、prod 拒启）
- [x] 对外签名服务 API（2026-08-12）= account + verification bc 各自暴露 `@RequireSignature` 签名端点；签名即准信、不约束消费方；登录只回 `SubjectView` 不发 token；与 admin bff 一视同仁；namespace 三层（OIDC 根 / `/api/sso/*` 浏览器 / 签名服务按 bc）
- [x] SSO 应用消费用户信息 = 读走 `/userinfo`、写走签名 `/api/account/*`（双身份：OIDC 消费方 + 签名调用方）
- [x] 不变式"应用不持有凭据" scope = SSO 浏览器链路（不约束签名服务调用）
- [x] 后台管理 API（2026-08-12）= admin-console 经签名服务；危险操作加管理调用方白名单（admin-console）过渡 gate；operator 走现成 RequestContext 透传零框架改动；新建 account_operation_log 审计（只写不查）；状态暴露 disable/activate/unlock（lock 不暴露、不定时解封）；登录历史独立工单做满（ADR-0010）
- [x] 后台不处理终端用户密码（2026-08-12）= admin 侧不暴露任何密码操作（重置 / 清除 / 强制改密都不做，合规）；密码改动只走用户自助（验证码重置 / 验旧密改密）；#71（admin 无码重置 + 清密码）实现后整体回退、#72（强制改密）一并 wontfix（ADR-0011，修订 ADR-0010 范围）
- [x] 审计基建 + 封号端点（#68）= `account_operation_log` 表（operator 显式取 RequestContext、不借 Auditable，TSID，无软删）+ `OperationType` 枚举（DISABLE 起，后续复用）；`POST /api/account/{userId}/disable`（reason 必填、`@RequireManagementCaller`、复用 `AccountStatusAppService.disable` 封号+踢人、同事务 append 审计）；解封/解锁/踢人随 #69
- [x] 解封 / 解锁 / 独立踢人端点（#69）= `OperationType` 扩 ACTIVATE/UNLOCK/REVOKE_SESSIONS；`POST /{userId}/activate`·`/{userId}/unlock`·`/{userId}/sessions/revoke`（均 `@RequireManagementCaller`、可选 reason body → 204）；activate/unlock 复用 `AccountStatusAppService`（不改会话）、revoke 复用 `SsoSessionRevoker.revokeQuietly`（不改状态、无会话撤销 0 个不报错），各自同事务 append 审计
- [ ] namespace 迁移 cookie/public → `/api/sso/*`（#55）
- [ ] 签名服务 API 落地（#56，前置 #55）
- [ ] identity-web 个人中心 me/profile/改密（后端就绪、前端未接，#57）
- [ ] per-app 操作权限细化（管理操作授权终态；本期 management-caller 白名单是过渡 stopgap，见 ADR-0010）—— #64
- [ ] 后台管理（二期）：后台直接开号 / 注销·删除账号 / 批量操作（封禁启用·导入·导出）/ 解绑社交账号（依赖 Phase 3 external_identities）—— #65
- [ ] 登录/安全事件流水（登录历史）—— 所有登录路径埋点 + 事件表 + 后台查询，独立做满（ADR-0010 范围切割）—— #63
- [ ]（推后/平台级）operator 独立成"BFF 登录用户"之外的一等概念（RequestContext 加 operatorId 字段 + OpenApiClient 出站白名单）→ 给 cartisan-boot 提 issue；本期方案②用现有 X-User-Id/X-User-Name，不需要
- [ ] 真实短信/邮件网关接入（计费/签名报备；邮件可配 mailpit 类假收件箱联调）— 独立排期
- [ ] 行为验证码（滑块类 SaaS）— 随真实短信通道一起评估
- [ ] MFA — 本期不做（除非未来特别需求）
- [ ] 组织/租户 — 推后 Phase 5
