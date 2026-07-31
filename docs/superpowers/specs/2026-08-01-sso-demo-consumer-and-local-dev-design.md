# 设计：SSO demo 消费方 + .localhost 本地联调（issue #16）

> 状态：已与用户确认形状（2026-08-01），待写实现计划。
> 对应：GitHub issue #16「[T2] demo 消费方 + 本地联调」，父 issue #14（SSO 登录闭环重做）。

## 背景与目标

identity 后端的 SSO 闭环已经建到 #15：`/authorize` 状态机、`/api/auth/login`、`/token`（发 access/id/refresh）、SSO cookie 会话（cookie+Redis+双超时）都在。但还缺三样东西让 SSO 真正「能被消费、能被验证」：

1. 一个**消费方参考实现**——证明「标准 cartisan-boot 前后端分离项目能接 SSO」，别的业务项目照抄即可。
2. **本地联调基建**——`identity.localhost` + `demo.localhost` 多域，免改 hosts、免证书，浏览器真跑通。
3. **dev 一键登**——identity-web 登录页还没建（在 `aieducenter-identity-web#1`，等本服务先收敛契约），需要一个 dev 侧入口让完整闭环现在就能跑通、反复联调。

identity-web 本期**等**——不催它动，只在收尾时给它发协调 issue。

### 关键事实（已核实）

- `/token`（`SsoTokenAppService`）发 access_token + refresh_token + **id_token**；id_token 带 `sub/email/email_verified/phone_number/nickname/picture/nonce` 等 claims（`NimbusJwtIdTokenSignerAdapter`）。
- `/userinfo`、`/jwks`、`/discovery` **未建**（issue #17）。→ 本期 demo 不调 `/userinfo`，用户信息从 id_token claims 解。
- `SsoProperties`：`loginPageUrl` 默认 `https://identity.localhost/login`；stub client `demo-client` / `demo-secret-please-change` / redirect `https://demo.localhost/auth/callback`（local profile 会覆盖）。
- `cartisan-boot/scripts/create-project.sh` 产出 Service/Gateway（Java+Spring Boot）和 Frontend（Next.js 15 + React 19）。**前后端分离 = 一个 Java 仓 + 一个 Next.js 仓**（如 studio + studio-web）。脚手架前端用 Next.js rewrite 代理 `/api/*`→后端（非 BFF、不持 token）。
- CONTEXT.md 钉死 BFF 拓扑：`/auth/callback` 是**业务后端（BFF）端点**、不是前端；token 只存 BFF 服务端；浏览器只持业务 cookie。

### 不变式（遵守）

- token 只存服务端（BFF 内存），浏览器不接触 token。
- dev 一键登**只走正常 code 流程**（建会话→发 code→BFF 用 code 换 token），**不做「指定 userId 直接发 token」捷径**（spec 明令禁止）。
- dev 一键登仅在 `local`/`dev` profile 注册，**prod 永不暴露**。
- demo 是消费方参考实现，不引入 operator / 不建用户表 / 不做业务角色。

## 总体形状

monorepo：在 `aieducenter-identity` 仓内新增 `demo/` 子目录（两个独立子项目，根 pom **不**收它们），加本地基建。

```
aieducenter-identity/
├── src/...                                  # 现有 identity IdP 后端
├── demo/                                    # 【新增】demo 消费方
│   ├── demo-backend/                        # 脚手架 service 当 BFF（OIDC 消费方）
│   └── demo-web/                            # 脚手架 frontend（Next.js，显示用户）
├── docker-compose.yml                       # 【新增/调整】本地 PG + Redis
├── dev-up.sh                                # 【新增】一键起本地全套
├── docs/guide/local-sso-debugging.md        # 【新增】.localhost 联调说明
└── src/main/resources/application-local.yml # 【调整】.localhost 覆盖
```

identity 后端本期新增两小块（都在现有 `sso` 上下文内）：
- `DevLoginController`（`@Profile({local,dev})`）——dev 一键登端点。
- `DevAccountSeeder`（`@Profile(local)`）——预置测试账号。

## 组件设计

### 1. identity 后端改动

#### 1.1 dev 一键登（`DevLoginController`）

- 路径：`GET /api/auth/dev-login`，仅 `@Profile({local,dev})` 注册，prod 不生效。
- 入参（query，由 `/authorize` 透传）：`client_id`、`redirect_uri`、`state`、`nonce`（同 `/authorize`；`scope` 不透传，与 `/api/auth/login` 一致）。
- 行为——**完全镜像 `SsoLoginAppService.loginByPassword`**，只把「密码认证」换成「取预置测试账号」：
  1. 校验 client（`requireActiveClient`）+ redirect_uri（`requireRedirectUri`）——和 `/api/auth/login` 同一道关。
  2. 取预置测试账号（`DevAccountSeeder` 种的 userId），`ensureLoginable()`。
  3. 建 SSO 会话：`sessionRepository.create(userId, displayLabel)`——和 login 同。
  4. 发 code：`codeService.issueCodeAndRedirect(userId, client, redirectUri, nonce, /*scope*/ null, state)`——和 login 同一条发码调用（scope 传 null，镜像 login）。
  5. 种 SSO cookie（`SsoCookieService`）——和 `/api/auth/login` 控制器同。
- 关键：**`AuthorizeController` 逻辑一行不改**。`/authorize` 无 cookie 时本来 302 到 `loginPageUrl`；只需在 `application-local.yml` 把 `identity.sso.login-page-url` 覆盖成 `http://identity.localhost:10001/api/auth/dev-login`，闭环自动接通。等 identity-web 落地，把这行换回登录页 URL 即可。

#### 1.2 dev 测试账号 seeder（`DevAccountSeeder`）

- `ApplicationRunner` / `@PostConstruct`，`@Profile(local)`。
- 启动时若账号不存在则建：`email=demo@aieducenter.com`、`password=demo12345`（BCrypt hash）、状态 active；profile（nickname「Demo 用户」）一并建。
- 已存在则跳过（幂等）。dev-login 就登这个 userId。
- **不放 Flyway 迁移**（迁移会进 prod）；用 profile-gated seeder 只在 local 跑。

#### 1.3 `application-local.yml` 覆盖

```yaml
identity:
  sso:
    login-page-url: http://identity.localhost:10001/api/auth/dev-login
    # redirect 白名单放行本地 demo callback（浏览器入口统一在 demo.localhost:3000）
    stub-redirect-uris:
      - http://demo.localhost:3000/auth/callback
```

> `cookie-secure` 保持 `true`——`.localhost` 在浏览器里是 secure context，HTTP 下也能种 Secure cookie。

### 2. demo-backend（BFF / OIDC 消费方）

脚手架生成 `service`，然后**剪掉 JPA/Flyway/Druid/PG**（demo 无业务数据，只持内存会话），只留 `spring-boot-starter-web` + 手写 OIDC 客户端逻辑。聚焦「SSO 接入」一件事。

**端口** `10010`；浏览器通过 `demo.localhost:3000`（Next 代理）访问，不直连。

**配置**（`application.yml` + `application-local.yml`）：
```yaml
sso:
  issuer: http://identity.localhost:10001          # identity IdP
  client-id: demo-client
  client-secret: demo-secret-please-change
  redirect-uri: http://demo.localhost:3000/auth/callback   # 浏览器入口
  scope: openid profile
app:
  base-url: http://demo.localhost:3000             # 前端地址（回跳用）
```

**端点**（`AuthController` + `MeController`，包 `com.aieducenter.demobackend.sso`）：

| 方法 路径 | 干什么 |
|---|---|
| `GET /auth/login` | 生成 `state`+`nonce`，存进短期 httpOnly cookie `oauth_txn`；302 到 identity `/authorize?client_id&redirect_uri&response_type=code&state&nonce&scope` |
| `GET /auth/callback?code&state` | 校 `state` 对 `oauth_txn`；服务端 POST identity `/token`（`grant_type=authorization_code`，`client_secret_post`）；拿到 access/id/refresh 存入**内存会话**（key=新 opaque sessionId）；种业务 cookie `demo_session`；清 `oauth_txn`；302 回 `app.base-url/` |
| `GET /api/me` | 凭 `demo_session` 取会话 → 解 id_token claims → 返回 `{userId(sub), email, nickname, picture}` |
| `POST /auth/logout` | 删内存会话 + 清 `demo_session` cookie → 302 回 `app.base-url/` |

- **会话存储**：`ConcurrentHashMap<String, BffSession>`（`BffSession{accessToken, idToken, refreshToken, userId, ...}`）。demo 专用，进程重启即丢，可接受。
- **业务 cookie**：`demo_session`，opaque 随机串，`httpOnly` + `Secure` + `SameSite=Lax`。
- **state/nonce**：`oauth_txn` cookie 存 `{state, nonce}`，callback 比对 `state`（防 CSRF）；`nonce` 留作 #17 验 id_token 用（本期 id_token 不验签，但仍透传写进 authorize，保持契约完整）。
- **调 identity /token**：`RestClient`（Spring 6），服务端带 `client_secret`，浏览器永不接触。失败 → 回前端登录页带 `?error`。
- **token 来源可信**：id_token 是 BFF 用自己的 client_secret 直接从 identity `/token` 拿的（受信机机通道），故本期**不验签**也能信任其 claims；验签留到 #17 引入 `/jwks` 后补（增量，不返工）。

### 3. demo-web（Next.js 显示页）

脚手架生成 `frontend`（端口 `3000`）。

- `next.config.mjs` rewrite：`/api/:path*` 和 `/auth/:path*` → `http://demo.localhost:10010`（`BACKEND_URL`）。浏览器只对 `demo.localhost:3000` 一个源。
- `/` 页面（client component）：mount 时 `GET /api/me`；200 → 显示用户（nickname / email / 头像）+ 「登出」按钮；401 → 显示「登录」链接（指向 `/auth/login`）。
- 「登出」→ `POST /auth/logout`（或链到 `/auth/logout`）。
- 最小 Tailwind 页面，沿用脚手架风格。

### 4. .localhost 本地联调基建

- **原理**：浏览器自动把 `*.localhost` 解析到 127.0.0.1（RFC 6761），且 HTTP 下当 secure context → Secure cookie 能种。故纯 HTTP + 端口，不用 hosts、不用 TLS、不用反向代理。
- **端口分工**：identity `identity.localhost:10001`；demo-backend `demo.localhost:10010`；demo-web `demo.localhost:3000`。
- **cookie 域**：SSO cookie（identity 种）域 = `identity.localhost`；业务 cookie（demo BFF 种）域 = `demo.localhost`。跨站 GET 跳转 + `SameSite=Lax` 满足 `/authorize`↔callback 往返。
- `docker-compose.yml`（仓根）：起 PG（`5432`）+ Redis（`6379`），给 identity 用。demo-backend 不连 DB。
- `dev-up.sh`：一键起 `docker compose up -d`（PG+Redis）+ 提示/启动 identity（`mvn spring-boot:run -Dspring-boot.run.profiles=local`）、demo-backend、demo-web 三个进程（脚本里给命令，三个进程可前台/后台跑）。
- `docs/guide/local-sso-debugging.md`：手把手——怎么起、访问哪、怎么验首次登录 + 二次免登、怎么排错（cookie/跳转）。

## 数据流

### 首次登录（identity-web 缺席，dev 一键登顶上）
```
浏览器 demo.localhost:3000/ →（未登录，显示「登录」）→ 点 /auth/login
demo-backend /auth/login：生成 state+nonce → 种 oauth_txn cookie
  → 302 identity.localhost:10001/authorize?client_id=demo-client&redirect_uri=...&state&nonce&scope
identity /authorize：校 client/redirect_uri → 无 SSO cookie
  → 302 login-page-url（local 覆盖为 /api/auth/dev-login，透传参数）
identity /api/auth/dev-login：校 client/redirect_uri → 取测试账号 userId
  → 建 SSO 会话 + 种 sso_session cookie → 发 code
  → 302 demo.localhost:3000/auth/callback?code&state
demo.localhost:3000/auth/callback →（Next 代理）→ demo-backend
  ：校 state → 服务端 POST identity /token(code+secret) → 拿 access/id/refresh 存内存会话
  → 种 demo_session cookie → 清 oauth_txn → 302 demo.localhost:3000/
demo.localhost:3000/ → 前端 GET /api/me（带 demo_session）→ BFF 解 id_token → 显示用户
```

### 二次免登（SSO 核心价值）
```
浏览器再进 demo.localhost:3000/ → /auth/login → 302 identity /authorize
identity /authorize：这次【有】SSO cookie（首次 dev-login 种的）→ 直接发 code → 302 回 demo callback
demo-backend /auth/callback：换 token → 种业务 cookie → 进首页
→ 用户无感进入（没再「登录」一次）
```

### 登出
```
demo /auth/logout → 删 BFF 内存会话 + 清 demo_session cookie → 302 回 /
（注：只登出 demo 业务会话；SSO 会话仍在 → 这就是 spec 的「准 SLO」，短期内二次访问 demo 仍免登。
 完整 RP-initiated logout 走 identity /logout 在 #19。）
```

## 测试策略

### identity（MockMvc 黑盒，复用 `SsoIntegrationTestBase`）
- 新增 dev-login 闭环用例（用 `@ActiveProfiles("local")` 或在 local-only bean 的测试里）：`/authorize`（无 cookie）→ 302 到 dev-login → 跟到发 code → `/token` 换 token 成功；二次 `/authorize`（带 SSO cookie）直接发 code。
- 断言外部行为：302 Location、Set-Cookie（sso_session）、`/token` 200 返 access/id/refresh。不 assert 私有方法。

### demo-backend（BFF OIDC 客户端逻辑）
- state 生成/校验、callback 拿 code 调 `/token`（**WireMock stub identity /token 响应**）、token 入内存会话、`/api/me` 解 id_token 返 claims、logout 清会话。
- 这些是 demo 作为「参考实现」的价值核心，要有真实测试。

### 浏览器闭环（真跑，不自动化）
- spec 明令「不引 Playwright/Selenium」；`dev-up.sh` 起来后手动走：首次登录、二次免登、登出。跨域 cookie 真实携带靠真跑验证。

### 架构守卫
- `ArchitectureTest` 补：dev-login 相关类落在 `sso` 上下文正确包；`@Profile` 守卫（可选）。

## 范围边界（本期不做）

- **不调 `/userinfo`、不验 id_token 签名**——#17 建好 `/userinfo`+`/jwks` 后再加（增量）。
- **不接真 app-registry**——继续用 stub client（`demo-client`）；真接入在 #6。
- **demo 不产品化**——内存会话（重启丢）、无 DB、无部署脚本（不需要部署 demo 到 prod）。
- **不做 RP-initiated logout / 完整 SLO**——#19；本期 demo 登出只清业务会话。
- **identity-web 不动工**——收尾发协调 issue（`aieducenter-identity-web`），把契约交代清楚等它接。

## 验收对照（issue #16）

| Acceptance | 怎么满足 |
|---|---|
| demo 发起 /authorize → 登录 → callback → 显示用户 | dev 一键登顶登录页位置，闭环全程真跑（首次登录数据流） |
| demo BFF 服务端换 token + 存 token + 种业务 cookie | demo-backend `/auth/callback`（内存会话 + demo_session cookie，浏览器不接触 token） |
| 二次访问 demo 免登 | SSO cookie 在 identity 侧 → `/authorize` 直接发 code（二次免登数据流） |
| .localhost 本地联调跑通 | `docker-compose` + `dev-up.sh` + `docs/guide/local-sso-debugging.md`，identity 后端 + demo 跑通（identity-web 暂缺席由 dev 一键登兜） |
| dev 测试账号一键登 | `DevLoginController` + `DevAccountSeeder`（local profile） |

## 收尾动作

- 给 `aieducenter-identity-web` 发 issue：交代 identity 这边契约——`/authorize` 状态机（有/无 cookie）、`/api/auth/login` 入参（credentials + authorize 参数）、SSO cookie（名/属性/域）、local profile 下 `/authorize` 跳转目标会从 dev-login 换成 identity-web 登录页（届时 `login-page-url` 配回登录页）。不催进度。
- 必要时更新本项目 `CONTEXT.md`「开发测试」段落（.localhost 端口分工、dev-login 兜底说明）。
