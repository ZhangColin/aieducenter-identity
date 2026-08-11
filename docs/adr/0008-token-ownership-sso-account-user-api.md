# ADR-0008: token 三件套归属 sso + account 应用层 = 平台用户一体化缝（落实 ADR-0004 决策3）

- 状态：已定
- 日期：2026-08-11
- 落实：#48
- 修订：[ADR-0002](0002-token-format-jwt.md)（加「归属 sso」）、[ADR-0004](0004-drop-satoken-single-sso-session.md)（决策3 标「已落实」）

## 背景
ADR-0004 决策3「token 是 OIDC 产物，归 `/token` 端点」定了**归属语义**（token 不归 login、归 OIDC），但落地只迁了**调用**（`SsoTokenAppService` 注入 `TokenIssuerAppService`），没迁**代码归属**——整套 token 实现（domain 端口 + 值对象 + `TokenIssuerAppService` + nimbus/redis 适配器）仍留在 `account`，靠 `account/package-info`「token 三件套签发，供 sso 复用」包装这个半成品。第一性验证：去掉 OIDC，这套 token 代码 account 一个都不需要——全是 OIDC 协议产物，与 account 本职（userId 锚点 + 凭据 + profile + 状态）零内聚。

更大的问题在 sso→account 的**十余条 domain 直穿**（见 ADR-0007 背景）：sso 替 account 做凭据校验（`AccountPasswordEncoderService.verifyPassword`）、做账号状态机（`ensureLoginable` / `recordLogin` / `save`）、读 account 聚合造 OIDC 声明。这把 account 的 domain 焊死，也让「account 作为平台用户基座被任意应用消费」无从谈起。

## 决策

### 1. token 三件套整体归属 sso
token 是 OIDC 协议产物，OIDC 端点全在 sso，代码归属随端点走：
- domain 端口 / 值对象（`AccessTokenSigner` / `Verifier`、`IdTokenSigner` / `Decoder`、`RefreshTokenStore`、`SigningKeyCatalog`、各 `*Claims` / `*View` / `VerifiedAccessToken`）→ `sso/domain/token/`；
- `TokenIssuerAppService` → `sso/application/`，签名改为 `issue(SubjectClaims, nonce, scope, sessionId)`——**纯 sso、零 account 依赖**；
- nimbus / redis 适配器 + `JwtTokenProperties` + `RsaKeyPairConfiguration` → `sso/infrastructure/token/`；
- `account/application/dto/response/LoginResponse`（`TokenIssuerAppService` 的返回类型，仅 sso 内部用、不出 HTTP）→ 迁 sso、更名 **`IssuedTokens`**；
- 配置前缀 `identity.token.jwt` **不变**（不断已部署 YAML，落位 sso 后命名略怪可接受）。

### 2. account 补齐 AppService 表面 = 平台用户一体化缝
account 作为平台终端用户身份基座 / 凭据权威，把「认人 / 建号 / 取 subject 数据」内聚成应用层契约，sso（以及未来的非-SSO 应用）只经这些面与 account 交往：
- `AccountAuthAppService`：
  - `authenticate(identifier, password) → SubjectView`——内聚「定位(email/phone) → 验码 → `ensureLoginable` → `recordLogin` → save」，**防枚举**（账号不存在↔密码错同一 `LOGIN_PASSWORD_INCORRECT`）；
  - `authenticateByIdentifier(identifier) → SubjectView`——验证码登录路径（无密码）；
  - `register(RegisterAccountCommand) → SubjectView`——唯一性 + 当场验码 + encode + 建号。
- `AccountSubjectAppService.subjectClaims(userId) → SubjectView`——OIDC 声明数据读缝（供 token 签发 / userinfo）。
- `SubjectView`（`userId / email / phone / nickname / avatar / status`）——auth / register / subject 查询**统一读模型**；`status` 承载可用性，**调用方自决 gate**（token 路径判 usable 抛、userinfo 路径忽略——保既有差异）。

### 3. sso→account 直接 AppService 注入（顺流，不起 port）
sso 的 AppService 直接注入 account 的 `AccountAuthAppService` / `AccountSubjectAppService`，按 ADR-0007 走「应用层 → 应用层」。sso 变薄为**纯会话 / OIDC 编排**：login / register / code-login / completion / token / userinfo 全调 account 拿 `SubjectView`，自管 SSO 会话 + 发 code；discovery / jwks / logout 全成 sso 上下文内引用（token 代码已归 sso），**#47（IdTokenDecoder 直穿）随之消解**。

### 4. account→sso 逆流保留 ACL port（断环，ADR-0007）
`SsoSessionRevocationPort` + `SsoSessionRevoker`（account 改密 / 封号时销毁 SSO 会话）保留——这是唯一的 account→sso 边，用 port 断 account↔sso 循环。不动。

### 5. dev 三件套删除（早期产物，条件已变）
`DevLoginAppService` / `DevLoginController` / `DevAccountSeeder`（issue #16，identity-web 登录页缺席时的免密兜底）+ `SsoProperties.devLogin` 配置块 + 相关测试删除。identity-web 登录页已在、密码登录 local 无摩擦（无验证码 / 图形码），原始立项理由消失。**local-dev 建号是独立问题、不进本次 scope**（真注册 / SQL / 文档化 curl）。

### 6. forward-path（本次不交付，记方向）
account 的 AppService 表面（auth / register / subject / password / profile / status）即**平台用户一体化缝**。SSO 应用经 sso 消费；**不做 SSO 的应用**将来可经这套表面的 **HTTP 机机 API（app-registry apikey 签名，CLAUDE.md「机机 用户数据/用户管理 API」）**直接消费——自己的登录 / 注册 / 个人中心，但用户与平台一体。本次只立应用层缝，**不建 HTTP 端点、不挂 apikey**；待需要时另开 ADR + ticket。

## 理由
- **归属对齐语义**：token 是 OIDC 产物，OIDC 端点在 sso，代码就该在 sso（ADR-0004 决策3 的完整落实）。
- **认人归凭据权威**：authenticate / register 是 account 的本职（CLAUDE.md「实现 authenticate()」），sso 只管会话 + OIDC。sso 替 account 做凭据活是历史包袱。
- **解耦换来自由**：account domain 可独立重构；sso 收敛到几个 AppService 面；平台用户一体化有了明确缝。
- **行为保真**：纯归属 + 缝的重划，OIDC wire 契约（/token /userinfo /jwks /discovery）与 login / register / token / userinfo 行为不变，identity-web / Demo 无感。

## 结果
- **好**：上下文边界清爽（ADR-0007 守护）；account 成平台用户基座可被多姿态消费；token 归属与语义一致；#47 消解；dev 早期脚手架清除。
- **代价**：account 新增 2 个 AppService + `SubjectView` + `RegisterAccountCommand`；sso 多数 AppService 改 wiring（调 account AppService 而非直穿 domain）；测试导入路径批量更新（被迁 token 类）+ 新 account AppService 单元测试。
- **行为保真红旗（实现时必验）**：
  1. `authenticate` 整块成 account 自己的 `@Transactional`，与 sso 建会话事务分离（大概率更正确，但是事务边界迁移）；
  2. `ensureLoginable`（登录）vs `ensureUsable`（发 token）语义差异需 `SubjectView.status` 都能表达，token 路径判、userinfo 路径忽略；
  3. `email_verified = (email != null)` 启发式照搬（可疑但属既有行为，正本清源另开）；
  4. 防枚举（账号不存在↔密码错同一异常）在 `account.authenticate` 内保持；
  5. OIDC wire 契约字节不变。
- **守护**：跨上下文边界靠 ADR-0007（推 cartisan-boot 的 ArchUnit）；本次结构正确性 PR 时 grep 核验「`sso` 不再 import `account.domain` / `account.infrastructure`」。

## 迁移文件清单（实现参考）

**token 全套 account → sso**

| 现位置（account） | 迁后（sso） |
|---|---|
| `domain/token/AccessTokenClaims` + `AccessTokenSigner` + `AccessTokenVerifier` | `sso/domain/token/` |
| `domain/token/IdTokenClaims` + `IdTokenSigner` + `IdTokenDecoder` | `sso/domain/token/` |
| `domain/token/RefreshTokenPayload` + `RefreshTokenStore` | `sso/domain/token/` |
| `domain/token/SigningKeyCatalog` + `JwkSetView` + `JwkView` | `sso/domain/token/` |
| `domain/token/VerifiedAccessToken` | `sso/domain/token/` |
| `application/TokenIssuerAppService`（签名改 `issue(SubjectClaims, ...)`） | `sso/application/` |
| `application/dto/response/LoginResponse` → 更名 `IssuedTokens` | `sso/application/dto/response/` |
| `infrastructure/token/JwtTokenProperties` + `RsaKeyPairConfiguration` + 6× `NimbusJwt*Adapter` / `NimbusSigningKeyCatalogAdapter` / `RedisRefreshTokenStoreAdapter` / `NimbusJwtSupport` | `sso/infrastructure/token/` |

**account 新增**

| 新文件 | 说明 |
|---|---|
| `application/AccountAuthAppService` | authenticate / authenticateByIdentifier / register |
| `application/AccountSubjectAppService` | subjectClaims(userId) |
| `application/dto/response/SubjectView` | 统一 subject 读模型（含 status） |
| `application/dto/command/RegisterAccountCommand` | register 入参 |

**sso 改 wiring（不再直穿 account domain）**

| 文件 | 改动 |
|---|---|
| `application/SsoLoginAppService` | 注入 `AccountAuthAppService`，调 `authenticate` |
| `application/SsoLoginCodeAppService` | 调 `authenticateByIdentifier` |
| `application/SsoRegisterAppService` | 调 `register` |
| `application/SsoLoginCompletionAppService` | 调 `AccountSubjectAppService.subjectClaims` 取 nickname |
| `application/SsoTokenAppService` | 调 `subjectClaims`；TokenIssuer 改为 sso 内调 |
| `application/SsoUserInfoAppService` | 调 `subjectClaims`（忽略 status，保既有）；AccessTokenVerifier 随 token 迁 sso 内 |
| `application/SsoLogoutAppService` | IdTokenDecoder 随 token 迁 sso 内（#47 消解） |
| `application/OidcDiscoveryAppService` + `endpoints/controller/JwksController` | SigningKeyCatalog / JwkSetView 随 token 迁 sso 内 |

**dev 三件套删除**

| 删除 | |
|---|---|
| `application/DevLoginAppService` + `endpoints/controller/DevLoginController` + `dev/DevAccountSeeder` + `dev/package-info` | 整删 |
| `config/SsoProperties.DevLogin` 字段 + `identity.sso.dev-login.*` 绑定 | 删 |
| `src/test/.../DevLoginFlowIntegrationTest` + `DevAccountSeederTest` + `SsoPropertiesTest` 的 devLogin 断言 + `ApplicationLocalYmlTest` 的 dev-login 防回退断言 | 删 |

**package-info**

| 文件 | 改动 |
|---|---|
| `account/package-info` | 删「token 三件套签发」职责条 |
| `sso/package-info` | 「复用 account TokenIssuerAppService」改述为「自有 token 签发 + 经 account AppService 取 subject 数据」 |

## 关联
- [ADR-0007](0007-cross-context-via-appservice.md)（跨上下文只走 AppService，本 ADR 的原则依据）
- [ADR-0002](0002-token-format-jwt.md)（token = JWT，格式不变；本 ADR 只改归属）
- [ADR-0004](0004-drop-satoken-single-sso-session.md)（决策3 由本 ADR 完整落实）
- #48、#47
