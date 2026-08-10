# aieducenter-identity — 限界上下文术语表

> 本服务 = 平台终端用户身份基座 / 集中式 IdP（基础能力层）。被所有业务应用消费（SSO 接入 + 机机用户数据 API）。无独立 UI（登录页在 identity-web）。
> **物理合一部署**：统一登录 BFF（`/authorize` + 登录闭环 + 种 SSO cookie）+ 用户域服务（`/token` `/userinfo` `/jwks` `/discovery`），一个进程、两层职责。
> 平台级术语（限界上下文 / IdP / SSO / 组织·租户 / 治理角色 / 对接契约…）见兄弟仓库 [../aieducenter-architecture/CONTEXT.md](../aieducenter-architecture/CONTEXT.md)，本文不重复，只记**本项目自身**演化出的术语与决策。

## 关键决策（2026-07-31 重审）

- **放弃 sa-token / cartisan-security 鉴权** — 见 [ADR-0004](docs/adr/0004-drop-satoken-single-sso-session.md)。cartisan-security 的甜区是「企业后台前端持 token 调自己后端」；identity 是 **IdP 不是消费应用**——三类接口（公开 / SSO 会话 / 机机签名 / OIDC 协议）没一类需要 sa-token 的 token-match 鉴权。原 [ADR-0003](docs/adr/0003-sso-satoken-self-built-oidc.md)「会话引擎继续 Sa-Token」被修订。
- **一套 SSO 会话**：identity 唯一用户会话 = IdP SSO 会话（cookie + Redis + 双超时）。不再有「access JWT 兼 Sa-Token 会话 token」那套。
- **token 是 OIDC 产物，归 `/token` 端点**：access/id/refresh 签给消费方 BFF，浏览器永不接触（BFF 模式）。login/register 不签 token 返回，只建会话发 code。

## 继承的稳定不变式（平台已定，本项目遵守）

- Operator 不在本服务（运营认证 + 角色在统一后台 admin）。
- SSO 对外走 OIDC（`/authorize` `/token` `/userinfo` `/jwks` `/discovery` `/logout`）；**token 只存消费方服务端，浏览器不接触 token**（BFF 模式）。
- 应用不持有用户凭据；社交登录归本域统一接入、归一到中央 Account。
- `tenantId` 全程可空（C 端无感，无租户按 userId 隔离）；写侧需自带租户过滤。
- **应用管理/登记不在本服务**——identity 消费 app-registry 的 SsoClient，不自建 client 表。
- 管组织治理角色（owner/admin/member）；应用内业务角色归应用自管。
- ~~鉴权机器用 cartisan-security~~ → ADR-0004 修订：identity 不用 cartisan-security 鉴权。

## 术语表（本项目，随讨论沉淀）

### IdP SSO 会话（SSO cookie）—— 本服务唯一用户会话
浏览器侧 SSO cookie（`httpOnly` + `Secure` + `SameSite-Lax`），**不透明 sessionId**（不是 token）。Redis 存 `sessionId → {userId, 创建时间, 最后活跃时间}`，**闲置 30 天 / 绝对 90 天**（可配）。
- 消费者：`/authorize` 判免登（有 cookie 直接发 code）；identity-web 凭它调 me/profile/改密/登出。
- 登录/注册/社交成功时种 cookie（identity 后端，物理含统一登录 BFF）。
- 区别于 access/id/refresh token（OIDC 产物，给消费方 BFF，不进会话）。

### token（OIDC 产物，归 `/token` 端点）
- **access_token**：JWT(RS256)，15min，claims iss/sub/aud/iat/exp/jti。
- **id_token**：JWT(RS256)，标准 OIDC 声明 + nonce + 按 scope 的 email/phone/name/picture。
- **refresh_token**：opaque 串，Redis 存，**一次性轮换**，**绑 SSO 会话**（会话过期即失效）。
- 签发在 `/token`（code 换 token）；浏览器永不接触。

### 接口命名空间
| 命名空间 | 干什么 | 谁调 |
|---|---|---|
| `/api/auth/*` | 认证入口：login / login-code / register——都建 SSO 会话 + 发 code；client-info（公开，登录页查应用名） | identity-web（浏览器） |
| `/api/account/*` | 账号管理：me / profile / change-password / reset-password | SSO 会话内 / 机机 |
| OIDC 根 | /authorize /token /userinfo /jwks /discovery /logout | 按协议 |

### 登录契约（/authorize 状态机 + /api/auth/login）
```
GET /authorize：验 client_id/redirect_uri/state → 看 SSO cookie
  有 cookie → 发 code, 302 回 redirect_uri?code&state          ← 二次 SSO
  无 cookie → 302 到登录页(透传 authorize 参数)
POST /api/auth/login {client_id, redirect_uri, state, nonce, scope, account, password}
  → 验凭据 → 建 SSO 会话 + 种 cookie → 发 code → 回 redirect_uri?code&state
POST /api/auth/login-code {同 authorize 透传 + account + code}
  → 验验证码(purpose=LOGIN) → 定位账号 → 建 SSO 会话 + 种 cookie → 发 code → 同上
POST /api/auth/register {同 authorize 透传 + email/phone + emailCode/phoneCode + password?}
  → 唯一性校验 → 当场验码(purpose=REGISTER) → 建号 → 注册即登录（同 login 后半段）
```
发 code 各处共用一个方法（login/register/login-code 经 SsoLoginCompletionAppService 统一后半段）；不引入 ticket/interactionId（最简方案）。scope 全链路透传（登录页 URL → login/login-code/register → 发码绑 code，#25）——首次登录与二次免登同参时 token scope/声明一致。
login/login-code/register 同时吃 **JSON 与 form-urlencoded**，**成功响应按提交方式分流**（#26）：
- **JSON 变体 = `200 {redirectUrl}`**——identity-web 登录/注册页是前后端分离 SPA：fetch POST JSON（同源，经 Next rewrite 代理），成功读体后 `window.location.href` 顶层导航回业务应用。不能回 302：fetch 会自动跟随、跨域跟随被 CORS 拦死（`redirect:'manual'` 也只拿到读不出 Location 的 opaqueredirect）。
- **form 变体 = `302 + Location`**——浏览器原生 form 顶层提交自然跟随（#23，无 JS 兜底保留）。
两变体同一 service、同一契约字段（camelCase：`clientId`/`redirectUri`/`state`/`nonce`/`scope`；form 只是编码差异，不是新契约——区别于 /authorize URL 参数的 snake_case）；Set-Cookie 种 SSO 会话两变体一致；失败响应（401/409/CODE_INVALID/400 `{error,error_description}`）两变体一致、不随提交方式变化。SSO 跨站链路不变式保持「跨站最后一跳 = 浏览器顶层导航、零跨域 fetch」（fetch 仅同源）。
验证码复用 verification 上下文：**purpose 分键**（REGISTER/LOGIN/RESET_PASSWORD，Redis key = 联络方式:用途），注册码与登录码互不串用；发码走 `/api/account/verification-code/*` 公开端点带 purpose。login-code **防用户枚举**：「账号不存在」与「验证码错误」同一响应（错码由 verification 抛 CODE_INVALID；验码通过但账号不存在补抛同一 CODE_INVALID），停用/锁定在验码通过后才告知。

### Account（账号 / 终端用户）
平台一个终端用户。一张 `account` 表：`userId`（主键 TSID，作 SSO `sub`，换邮箱/手机不变）；登录定位字段 `email`/`phone`（唯一、可空）+ `password_hash`（可空）；状态字段。
> email/phone 是「登录入口 + 联络通道」，**不是身份本体**；身份本体是 userId。

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
- 落地分期：**密码注册（#18）+ 当场验码/密码可选/验证码登录（#22）均已落地**（`/api/auth/register` + `/api/auth/login-code`；「至少一联络方式」与格式校验收在 `Account.register` 聚合不变量）。**#28 拍板（2026-08-02）：前端这期接验证码 UI**——identity-web Phase 1「无码注册」拍板推翻（#5 返工），email/phone 注册登录 + 图形码一次做全；dev 联调靠固定码，**不做缺码放行**，契约保持「码永远必填」。注册页密码字段 UI 这期**必填**（后端契约仍支持可选，「纯验证码登录用户」形态等有需求再放开）。

### 社交登录（微信扫码首批）
**两层 OAuth**：identity 对业务应用 = IdP，对微信 = 客户端。
```
点"微信登录" → identity 把 OIDC 上下文存 Redis(绑 ticket,微信 state=ticket) → 跳微信扫码
微信回调 /api/auth/social/wechat/callback?code&state=ticket → 凭 ticket 取回 OIDC 上下文
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
| `login.company.com/` | identity-web（SPA 静态） | login.company.com |
| `login.company.com/authorize` · `/api/*` | identity 后端 | login.company.com（SSO cookie） |
| `login.company.com/token` · `/userinfo` | identity 后端 | —（机机，不带 cookie） |

钉死三点：① `/authorize` 与 identity-web **同域**（SSO cookie 才带得上，分域即废）；② `/auth/callback` 是**业务 BFF 端点**（非前端）；③ `/token` **机机直连**（带 client_secret）。`SameSite=Lax`（扛跨站跳转）。

## 开发测试

- **本地**：`.localhost` 多域——浏览器自动解析到 127.0.0.1 + 当 secure context（免改 hosts、免证书）。端口：identity `identity.localhost:10001`、identity-web 登录页 `identity.localhost:10002`（Next dev，rewrite `/api/*`→:10001）、demo BFF `demo.localhost:10010`、demo-web `demo.localhost:3000`（Next 代理 `/api`·`/auth`→BFF）。一键起：`./dev-up.sh`（PG+Redis+四进程命令）；手册 `docs/guide/local-sso-debugging.md`。
- **demo 消费方**（仓内 `demo/`：`demo-backend` BFF + `demo-web`）：发起 /authorize + 收 callback + BFF 换 token（内存存、浏览器不接触）+ 显示用户。一身三任：**测试必需品 + 对接活示例 + 演示开发姿态**。
- **dev 一键登**（identity-web 缺席时的手工兜底，#16/#27）：`identity.sso.dev-login.enabled=true`（local profile）保持开启、预置账号照常种入；但 local 的 `login-page-url` 已指向 identity-web 登录页（`identity.localhost:10002/login`），`/authorize` 无 cookie 默认 302 到真实登录页。identity-web 没起时手工访问 `/api/auth/dev-login`（带 authorize 参数）自动登预置账号 `demo@aieducenter.com`、发 code。仍走正常 code→/token 流程（不直接发 token）。
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
- **本地缓存（#30 基础 + #32 完整韧性）**：Caffeine 按 clientId 缓存 `SsoClientCacheEntry`（值 + 写入时刻），`maximumSize` 上限、**无时间淘汰**（last-known-good 尽量驻留以兜底）。① **fresh 窗口 30min**（写入起算）命中缓存不打远程——`/authorize` 高频查询不轰 app-registry；② **负面缓存**——`active=false` / `clientSecretHash=null` / **404「查不到」** 入缓存（`Optional.empty()`），禁用/不存在的 client 不反复打 app-registry；③ **抖动降级**——app-registry 抖动（5xx/连接失败/超时/其它非 404 错）时 **有过期缓存（哪怕 negative）就兜底**（值即最近一次成功解析、不返脏数据）、**无缓存就拒办**（返 empty，上层转 `invalid_client`/`unauthorized_client`；属 infra 故障，热路径上按 [ADR-0006](docs/adr/0006-error-response-split-by-caller.md) 改 `temporarily_unavailable`(503)、与 client 配置错区分），不抛 500。404 不视为抖动（是「查不到」的稳定结论，走负面缓存）。fresh/stale 判定走注入的 `Clock`（测试 `MutableClock` 推进时间确定性触发过期）。
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

## 构建分期（重排；旧 issue #5 等及 token-in-login 设计废弃）

- **Phase 1（SSO 会话基建 + 登录闭环）**：SSO 会话（cookie + Redis + 双超时）+ `/authorize` + 登录契约（`/api/auth/login`）+ 接口命名空间重组 + demo 消费方 + dev 环境。
- **Phase 2（OIDC 协议端点）**：`/token` + `/userinfo` + `/jwks` + `/discovery`。
- **Phase 3（注册 + 社交）**：注册流 + 微信扫码（PC）。
- **Phase 4（登出）**：RP-initiated logout + 准 SLO + 改密/封号踢人。
- **Phase 5（组织/租户，推后）**：成员关系 + 治理角色 + 写侧租户过滤。

## 待决议题

- [x] sa-token 去留 = **弃**（ADR-0004）
- [x] SSO 会话 = **一套 cookie 会话**；token 归 `/token`
- [x] 登录契约 = `/authorize` 状态机 + `/api/auth/login` 最简（无 ticket）
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
- [ ] 真实短信/邮件网关接入（计费/签名报备；邮件可配 mailpit 类假收件箱联调）— 独立排期
- [ ] 行为验证码（滑块类 SaaS）— 随真实短信通道一起评估
- [ ] MFA — 本期不做（除非未来特别需求）
- [ ] 组织/租户 — 推后 Phase 5
