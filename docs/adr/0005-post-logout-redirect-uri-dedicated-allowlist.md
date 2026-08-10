# ADR-0005: post_logout_redirect_uri 用独立白名单，不再复用 redirect_uri

- 状态：已定
- 日期：2026-08-10
- 修订：[CONTEXT.md](../../CONTEXT.md) 登出条目与安全集中「post_logout_redirect_uri 同 redirect_uri（校验）」的表述

## 背景

RP-Initiated Logout 的 `post_logout_redirect_uri`（登出后把浏览器带回应用的 URI）原本复用 `SsoClient.redirectUris` 这一个白名单做精确匹配——登录回调、登出回调查的是同一个 Set。

两层问题：

1. **术语/职责混淆**：`redirectUris` 在 OIDC 里专指「授权码登录回调」，拿来同时管「登出回调」是语义过载。两者安全考量不同（登录回调换 code、登出回调只是回跳展示），本就是两个概念。
2. **直接 bug**：demo 登出时发 `post_logout_redirect_uri=http://demo.localhost:3000/`（首页），但 demo 这个 client 的 `redirectUris` 只登记了登录回调 `http://demo.localhost:3000/auth/callback`，**没有首页**。精确匹配不上 → `SsoLogoutAppService.resolvePostLogoutRedirect` catch 返 null → `LogoutController` 返 `200 ok().build()` → 浏览器停在 `/logout?...` 看空白页。即用户报告的「登出卡在 identity /logout 不跳回应用」。

## 决策

1. `SsoClient` 加独立字段 `postLogoutRedirectUris`（`Set<String>`，**多值**，与 `redirectUris` 同为 Set），与 `redirectUris` 平级、各自精确匹配（不做前缀/通配）。
2. 登出校验改用 `requirePostLogoutRedirectUri(client, postLogoutRedirectUri)`，与登录的 `requireRedirectUri` 分开。
3. app-registry 的 sso-client facet 加列 `post_logout_redirect_uris`，API 返回该字段；各 client 注册时分别填登录回调与登出回跳 URI（demo 登出回跳填首页 `http://demo.localhost:3000/`）。

## 理由

- **术语清晰、职责分明**：登录回调和登出回调是两个概念，分两个白名单后代码即文档。OIDC 规范里 `redirect_uris` 与 `post_logout_redirect_uris` 本就是两个独立字段，本服务对齐规范。
- **安全收放独立**：登出回跳 URI 集合可与登录回调不同（某 client 可能登录回调多、登出只回首页，或反之），互不牵连。
- **消除别扭操作**：不再有「为了让登出能回首页，得把首页塞进登录回调白名单」这种污染登录白名单的做法。
- **多值能力保留**：规范复数；同 client 跨多端（web/移动）或多登出落点时需要，当前各应用实际填一两个，能力留着。

## 后果

- **app-registry（跨仓，发 issue）**：client 表加列 `post_logout_redirect_uris` + Flyway 迁移 + `/api/app-registry/sso-clients/{clientId}` 返回新字段 + demo client 注册数据填首页 URI。
- **identity 本仓**：`SsoClient` record 加 `postLogoutRedirectUris`；`RemoteSsoClientRepositoryAdapter` 映射新字段；`SsoClientValidationService` 加 `requirePostLogoutRedirectUri`；`SsoLogoutAppService.resolvePostLogoutRedirect` 改用新校验。
- [CONTEXT.md](../../CONTEXT.md) 登出条目、安全必需集同步修订（去掉「同 redirect_uri」）。
- 不兼容点：app-registry 未下发新字段前，identity 侧按「字段缺失 = 登出回跳白名单为空 = 不跳转」兜底，不阻断登出主流程（会话照清）。

## 关联

- [CONTEXT.md](../../CONTEXT.md)（登出 / 安全必需集）
- app-registry sso-facet（`CONTEXT.md` §跨项目集成）
- 触发现场：登出卡在 `/logout` 返 200 空白
