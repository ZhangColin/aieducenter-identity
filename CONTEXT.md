# aieducenter-identity — 限界上下文术语表

> 本服务 = 平台终端用户身份基座 / 集中式 IdP（基础能力层）。被所有业务应用消费（直调用户接口 + SSO 接入）。无 UI（登录页在 identity-web）。
> 平台级术语（限界上下文 / 基础域 / IdP / SSO / 组织·租户 / 治理角色 / 对接契约…）见兄弟仓库 [../aieducenter-architecture/CONTEXT.md](../aieducenter-architecture/CONTEXT.md)，本文不重复，只记**本项目自身**演化出的术语与决策。

## 继承的稳定不变式（平台已定，本项目遵守）
- Operator 不在本服务（运营人员认证 + 角色/部门/岗位在统一后台 admin）。
- SSO 对外走 OIDC（`/authorize` `/token` `/userinfo` `/jwks` `/discovery`）；token 只存服务端，浏览器不接触 token（BFF 模式）。
- 应用不持有用户凭据；社交登录（微信/Google/Apple）归本域统一接入、归一到中央 Account。
- `tenantId` 全程可空（C 端无感，无租户按 `userId` 隔离）；写侧需自带租户过滤。
- 鉴权机器用 cartisan-security（共享库、不建用户表）——本服务自带 account 凭据表、实现 `authenticate()`/`StpInterface`；只 Account 一种身份，Sa-Token 默认 loginType。
- 管组织治理角色（owner/admin/member）；应用内业务角色归应用自管。

## 术语表（本项目，随讨论沉淀）

### Account（账号 / 终端用户）
平台一个终端用户。**一张 `account` 表**：
- `userId`（主键，TSID）——唯一稳定身份，作 SSO `sub`。换邮箱/换手机它都不变。
- 登录定位字段：`email`（唯一、可空）、`phone`（唯一、可空）、`password_hash`（可空）——注册/登录靠它们「找到是哪个用户」。
- 状态字段：状态、是否锁定、最后登录时间等。

> email/phone 是「登录入口 + 联络通道」，**不是身份本体**；身份本体是 `userId`。它们全局唯一是为了能定位用户。

### Profile（个人信息）
昵称、头像等个人资料，放**扩展表**（跟 `account` 1:1），不塞进用户主表。

### External Identity（第三方绑定）
**一张 `external_identities` 表**：`provider`（微信/Google/Apple）+ `provider_uid`（unionid/openid）+ `userId`。社交登录靠它定位到用户、归一到同一个 Account。一张表支持多 provider 绑同一用户。

### 凭据（Credential）
用户用来证明「我是这个 userId」的东西——密码（存 hash）、邮箱/手机验证码（走 Redis 一次性）、第三方 token（不存）。登录 = 用任一凭据证明 → 解析到 `userId` → 签发会话/token。

> 前瞻（未定，仅记录）：若将来要「一个账号绑多个同类型凭据」（如多手机号、passkey），内联字段不够再加表。MFA **本期不做**（除非未来特别需求），现模型不预设方案。

### 注册（Account 诞生）
- **开放注册**：谁都能注册（C 端拉新）；邀请制留到组织/租户期（B2B/团队）。
- **至少留一个联络方式**（email 或 phone，二选一）；填了就**当场发码验证、验过才建号**（防刷号、保证联络方式真实）。验证码走 verification 上下文（从 studio 迁）。
- **仅社交可建号**（微信登录直接建），但建号后**引导补一个联络方式**（不阻断、只提示）——否则找回是坑。

### 社交登录（第三方登录）
用户用第三方账号（微信/Apple/Google…）登录、归一到同一个 Account。机制共用：`external_identities` 表(provider + provider_uid + userId) + OAuth 回调控制器 + state/PKCE(防 CSRF) + 绑定/解绑流程。机制一次立稳，加 provider 只是接各家 API。
- **首批：微信**——用 **unionid** 作 `provider_uid`（微信开放平台下同主体多应用共享），跨应用归一到同一 userId。
- **Apple/Google/其它**：以后按需再加（机制已立、接各家 API 即是）。

### IdP 会话（SSO cookie）
用户在 IdP 登录后，IdP 种的浏览器侧 SSO cookie——SSO「免重复登录」的核心：第二个应用跳 `/authorize` 时，IdP 看到这 cookie 还在 → 免登录直接发 code。区别于 BFF 服务端存的 access/refresh token（两者独立、一般对齐）。
- cookie：`httpOnly`+`Secure`+`SameSite=Lax`（架构定）。
- 有效期：**闲置 30 天 + 绝对 90 天**（可配）。
- token 续命独立但与会话对齐：会话过期 → 重登；refresh token 用到它自己过期。
- **SLO（单点登出）**：排最后做——清 IdP 会话 + 各 BFF 清自己 cookie；access_token 短命自然过期。

### OIDC 端点（对外契约，全部进首轮）
本服务对外暴露的标准 OIDC 端点（**不加任何非标准私货端点**——要加就是过度设计）：
- `/discovery`（`.well-known/openid-configuration`）：配置清单 JSON，对接方自动发现端点/scope/jwks 位置。零成本标配。
- `/jwks`（`/oauth2/jwks`）：验签公钥集，对接方自验 id_token 签名。
- `/authorize`：浏览器入口，弹登录 + 发一次性 code。
- `/token`：code 换 token（BFF 服务端调）。
- `/userinfo`：access_token 换用户资料（按 scope）。
- `/logout`（SLO）：单点登出（首轮排在最后）。

## 身份域术语速查（说人话）
- **IdP（Identity Provider，身份提供者）**：「发身份证的机构」。本服务=平台 IdP，权威记着每个用户是谁、给各应用发通行证(token)。应用不信用户自报，只认 IdP 发的证。
- **SSO（Single Sign-On，单点登录）**：一次登录、所有接入应用都认、不用重复登录。
- **OIDC（OpenID Connect）**：SSO 的标准协议（规定端点 + token 格式）。走标准=任何语言有现成库、对接容易。
- **BFF（Backend For Frontend）**：应用自己的后端当前端浏览器的「代办」。SSO 里 token 存 BFF 服务端、浏览器只持 cookie、token 不进浏览器（防 XSS）。
- **MFA（Multi-Factor Authentication，多因素认证 / 两步验证）**：密码之外再要一样东西证明是你（手机码 / Google 身份验证器 6 位码 / 指纹）。**本服务本期不做**（除非未来特别需求）。
- **TOTP**：MFA 的一种实现——Google 身份验证器那种 30 秒一变的 6 位码（基于时间）。
- **SLO（Single Logout，单点登出）**：一处登出、所有应用登出（对应 SSO）。
- **token（通行证）**：登录后 IdP 发给应用的凭证。OIDC 三种：**id_token**（证明是谁，JWT）、**access_token**（调资源接口，本服务=JWT）、**refresh_token**（access 过期后换新，不透明串服务端存）。
- **JWT（JSON Web Token）**：「盖公章的介绍信」——token 自带信息 + 签名，对接方用 IdP 公钥本地验、不用回 IdP。
- **opaque（不透明串）**：「存包牌」——本身不带信息，必须回 IdP 查。本服务 refresh_token 用它。
- **/jwks**：IdP 的公钥集端点，对接方拿它本地验 JWT 签名。

## 决策记录
见 [docs/adr/](docs/adr/)。

## 构建分期（本轮建模一次到位，构建按依赖切片）
- **Phase 0（卫生）**：从 studio 迁账号基线 + 修必修 bug——开工清单见 [docs/migration-from-studio.md](docs/migration-from-studio.md)。
- **Phase 1（token/会话）**：token 升级（access+refresh+id_token）+ IdP 会话（SSO cookie）+ `/me`。
- **Phase 2（OIDC）**：`/authorize`(BFF) `/token` `/userinfo` `/jwks` `/discovery` + 应用(client)注册。
- **Phase 3（社交）**：`external_identities` + 微信优先。
- **Phase 4（组织/租户，推后）**：成员关系 + 治理角色 + 写侧租户过滤。靠 `tenantId` 可空解耦，0–3 可先上线。

## 跨项目集成：App Registry（已定，已实现）
`aieducenter-openapi` 已升级重命名为 **aieducenter-app-registry**（平台「应用/消费方」单一登记处，基础能力层），实现「一个 app + 两 facet」：`RegisteredApp`（`app_code` 稳定 slug 不可变）+ 签名 facet（`ApiKey`，机机验签）+ SSO facet（`SsoClient`，供 OIDC）。

**identity 的 client 注册 = 消费 app-registry，不自建 oauth_client 表**：
- **契约**：`GET /api/app-registry/sso-clients/{clientId}` → `SsoClientInfo{clientId, appId, clientName, clientSecretHash(argon2), redirectUris, scopes, grants, active}`（bootstrap 端点，内网无验签、网络边界即信任边界）。
- **identity 侧**：定义「client 查询端口」(interface) → 远程适配器调 bootstrap 端点 + 本地缓存（类 `RemoteApiKeyProvider` Caffeine 30min）。
- **secret 认证**：`client_secret_post`——应用发明文 client_secret，identity 用 argon2 `matches()` 比对 hash（hash 自描述含参数+salt，identity 独立可验，依赖 spring-security-crypto + BouncyCastle）。**hash-only 不变式**：identity 永不拿明文。
- **组合状态**：`active = client.active && app.active`；任一禁用 → identity 拒办 SSO。
- **边界**：app-registry 只持 client 元数据 + 提供数据；OIDC 流程逻辑（client 认证比对、code/token 签发、登录流）全在 identity。

来源：app-registry `CONTEXT.md` + ADR-0003(sso-facet) + 架构起步包 `docs/starters/app-registry.md`。

## 本次待决议题
- [x] 本次范围与分期
- [x] SSO 实现选型 = Sa-Token 自搭 OIDC — 见 [ADR-0003](docs/adr/0003-sso-satoken-self-built-oidc.md)（oauth2 模块覆盖度实现期评估）
- [x] token 形态 = JWT（id_token + access_token；refresh opaque）— 见 [ADR-0002](docs/adr/0002-token-format-jwt.md)
- [x] Account 锚点与凭据模型 — 见 [ADR-0001](docs/adr/0001-account-credential-model.md)
- [x] 注册策略 = 开放注册 + 至少一联络方式(当场验证) + 仅社交引导补绑
- [x] 社交登录首批 = 微信（unionid 归一）；Apple/Google/其它以后按需
- [x] MFA — 本期不做（清除；除非未来特别需求）
- [x] IdP 会话 = SSO cookie（httpOnly+Secure+SameSite=Lax）/ 闲置30·绝对90 / token 续命对齐 / SLO 排最后
- [x] 应用(client)注册 = 消费 aieducenter-app-registry（bootstrap 端点 + argon2 比对 + 本地缓存），identity 不建 oauth_client 表
- [x] 组织/租户 — 推后 phase 4；模型沿用平台定义（arch CONTEXT.md 已 crisp），本轮不重 grill
