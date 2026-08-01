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
| `/api/auth/*` | 认证入口：login / login-sms / register——都建 SSO 会话 + 发 code | identity-web（浏览器） |
| `/api/account/*` | 账号管理：me / profile / change-password / reset-password | SSO 会话内 / 机机 |
| OIDC 根 | /authorize /token /userinfo /jwks /discovery /logout | 按协议 |

### 登录契约（/authorize 状态机 + /api/auth/login）
```
GET /authorize：验 client_id/redirect_uri/state → 看 SSO cookie
  有 cookie → 发 code, 302 回 redirect_uri?code&state          ← 二次 SSO
  无 cookie → 302 到登录页(透传 authorize 参数)
POST /api/auth/login {client_id, redirect_uri, state, nonce, credentials}
  → 验凭据 → 建 SSO 会话 + 种 cookie → 发 code → 302 回 redirect_uri?code&state
```
发 code 两处共用一个方法；不引入 ticket/interactionId（最简方案）。

### Account（账号 / 终端用户）
平台一个终端用户。一张 `account` 表：`userId`（主键 TSID，作 SSO `sub`，换邮箱/手机不变）；登录定位字段 `email`/`phone`（唯一、可空）+ `password_hash`（可空）；状态字段。
> email/phone 是「登录入口 + 联络通道」，**不是身份本体**；身份本体是 userId。

### Profile（个人信息）
昵称/头像等，扩展表（跟 account 1:1），不塞进用户主表。

### 凭据（Credential）
证明「我是这个 userId」的东西——密码(hash) / 邮箱手机验证码(Redis 一次性) / 第三方 token(不存)。登录 = 用任一凭据证明 → 解析 userId → 建 SSO 会话 + 发 code。

### External Identity（第三方绑定）
一张 `external_identities` 表：`provider` + `provider_uid` + `userId`。社交登录靠它定位/归一，一张表支持多 provider 绑同一用户。

### 注册
- 开放注册；**至少一联络方式**(email/phone 二选一)**当场发码验证、验过才建号**；**密码可选**（设了密码登，没设验证码登）。
- **注册即登录**：建号 → 建 SSO 会话 → 发 code（同登录后半段，不再单独登一次）。
- 仅社交可直接建号 + 引导补联络方式（不阻断）。

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
- **RP-initiated logout**：应用发起跳 `GET /logout?client_id&post_logout_redirect_uri&state` → 清 SSO 会话 + 清 cookie → 302 回 `post_logout_redirect_uri`（预注册 + 校验，同 redirect_uri）。
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

- **本地**：`.localhost` 多域——浏览器自动解析到 127.0.0.1 + 当 secure context（免改 hosts、免证书）。端口：identity `identity.localhost:10001`、demo BFF `demo.localhost:10010`、demo-web `demo.localhost:3000`（Next 代理 `/api`·`/auth`→BFF）。一键起：`./dev-up.sh`（PG+Redis+三进程命令）；手册 `docs/guide/local-sso-debugging.md`。
- **demo 消费方**（仓内 `demo/`：`demo-backend` BFF + `demo-web`）：发起 /authorize + 收 callback + BFF 换 token（内存存、浏览器不接触）+ 显示用户。一身三任：**测试必需品 + 对接活示例 + 演示开发姿态**。
- **dev 一键登**（identity-web 登录页缺席时的兜底，#16）：`identity.sso.dev-login.enabled=true`（local profile）时，`/authorize` 无 cookie 跳 `/api/auth/dev-login`，自动登预置账号 `demo@aieducenter.com`、发 code。仍走正常 code→/token 流程（不直接发 token）。identity-web 登录页落地后，把 local 的 `login-page-url` 换回登录页即可。
- **dev SSO 环境**（identity.dev.aieducenter.com）：真实 OIDC；redirect_uri 放行 `localhost:*`；预置测试账号 + 一键快速登录；跳过验证码/短信实发。**不做「指定 userId 直接发 token」捷径**。
- **消费方认证解耦**：业务代码只认「当前登录用户」抽象；姿态 A 连 dev SSO（主线）/ 姿态 B 本地 mock（兜底，消费方自写，不给 mock 端点）。

## 安全必需集

- `redirect_uri` **精确匹配**白名单（查 app-registry），不做前缀/通配；不匹配时**不重定向**、渲染错误页（防开放重定向）。
- `code` **一次性 + 60s + 绑 client/redirect_uri**，换完即删。
- `state` 防 CSRF（消费方生成 + 回调比对）；`nonce` 防重放（id_token 回带）。
- PKCE **支持不强制**（BFF 机密客户端靠 client_secret）。
- `client_secret` **argon2 hash-only 比对**，永不返回明文。

## 错误处理（OIDC 标准）

- `/authorize` 重定向前错（client_id/redirect_uri 无效或缺）→ **不重定向**，渲染错误页。
- `/authorize` 其它错 → 302 回 `redirect_uri?error&state`；用户取消 → `error=access_denied`。
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

## 跨项目集成：app-registry（已实现）

`aieducenter-app-registry`（平台「应用/消费方」单一登记处，基础能力层）。identity 的 client 注册 = **消费**其 SsoClient facet，不自建 oauth_client 表。
- 契约：`GET /api/app-registry/sso-clients/{clientId}` → `SsoClientInfo{clientId, appId, clientName, clientSecretHash(argon2), redirectUris, scopes, grants, active}`。
- identity 侧：client 查询端口 → 远程适配器调 bootstrap 端点 + 本地缓存（Caffeine 30min）。
- secret 认证：`client_secret_post`，argon2 `matches()` 比对（hash-only，永不拿明文）。
- 组合状态：`active = client.active && app.active`；任一禁用 → identity 拒办 SSO。
- **应用管理/登记**（注册 app、加 SSO facet、填 redirect_uri、取 client_secret）在 app-registry / 统一后台，**不在本服务**。

来源：app-registry `CONTEXT.md` + ADR-0003(sso-facet) + 起步包 `docs/starters/app-registry.md`。

## 决策记录
见 [docs/adr/](docs/adr/)：
- [ADR-0001](docs/adr/0001-account-credential-model.md) Account 锚点与凭据模型
- [ADR-0002](docs/adr/0002-token-format-jwt.md) token = JWT
- [ADR-0003](docs/adr/0003-sso-satoken-self-built-oidc.md) SSO = Sa-Token 自搭 OIDC（**「会话引擎继续 Sa-Token」被 ADR-0004 修订**）
- [ADR-0004](docs/adr/0004-drop-satoken-single-sso-session.md) **弃 sa-token / 一套 SSO 会话 / token 归 /token**

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
- [x] 应用管理/登记 = 不在本服务，消费 app-registry
- [ ] MFA — 本期不做（除非未来特别需求）
- [ ] 组织/租户 — 推后 Phase 5
