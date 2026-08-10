# ADR-0006: OIDC 端点错误响应按调用方分流——浏览器类跳 identity-web 兜底页，机机类保 RFC6749 JSON

- 状态：已定
- 日期：2026-08-10
- 关联 issue：[#33](https://github.com/.../issues/33)（/authorize 浏览器侧错误 raw JSON + infra 误报 unauthorized_client）

## 背景

`OidcExceptionHandler`（`@RestControllerAdvice`）全局把所有 `OidcException` 一律转成 RFC6749 的 `{error, error_description}` JSON——**不区分端点是浏览器顶层导航，还是机器用 HTTP 客户端调用**。两层后果：

1. **浏览器类端点给人看 JSON**：`/authorize`（用户在浏览器地址栏/跳转触发）参数不对时，用户看到的是裸 `{"error":"invalid_request",...}`，不是人能看的页面。`/logout` 校验失败更特殊——异常被 `resolvePostLogoutRedirect` 内部 catch 掉，返的是 `200 ok().build()` 空白，用户看白屏卡在 `/logout?...`。
2. **infra 故障误报成配置错（#33-B）**：`RemoteSsoClientRepositoryAdapter` 的抖动降级（#32）在 app-registry 不可用时返 empty → 上层转 `unauthorized_client`(400)。但 app-registry 不可用是 infra 故障，按 RFC 6749 §4.1.2.1 应是 `temporarily_unavailable`(503)。当前错误码把人引到「是不是没注册」的歧路。

identity 后端是纯 API（`src/main/resources` 无 `static/`/`templates/`），所有给人看的 UI 由 identity-web（同部署一体的 Next.js）渲染。

## 决策

按**调用方**分流错误响应：

| 端点类 | 端点 | 出错响应 |
|---|---|---|
| 浏览器导航类 | `GET /authorize`、`GET /logout` | **302 跳 identity-web 兜底页** `/error?error&error_description&client_id`，由前端渲染友好页 |
| 机机调用类 | `POST /token`、`GET /userinfo`、`GET /jwks`、`GET /.well-known/openid-configuration` | **维持 RFC6749 JSON** `{error, error_description}`（机器要解析 error code，不能改） |

实现：

1. **分流用浏览器类 Controller 的局部 `@ExceptionHandler`**（局部优先于全局 `OidcExceptionHandler`），返 302 跳 `identity.sso.error-page-url`（local → identity-web `/error`）。不污染机机类端点。
2. **`/logout` 失败分支归入兜底**：不再 catch 返 200 空白，改走兜底页（提示「已登出但未能自动返回应用」——此时 SSO 会话已清）。
3. **infra 故障错误码修正（#33-B）**：`RemoteSsoClientRepositoryAdapter` 抖动降级「无缓存拒办」路径，由 `unauthorized_client`(400) 改为 `temporarily_unavailable`(503)。404「查不到」仍走负面缓存 → `unauthorized_client`（这是稳定的「不存在」结论，不是抖动）。
4. identity-web 新增 `/error` 路由，收 `error`/`error_description`/`client_id` 渲染兜底页（样式对齐 `AuthShell`/`InvalidLinkNotice`，client_id 命中注册时可给「返回应用」安全链接）。

## 理由

- **各得其所**：浏览器类端点的受众是「人」，要给人能看的页；机机类端点的受众是「代码」，要给可解析的结构化错误。一刀切 JSON 或一刀切 HTML 都错。
- **UI 归 identity-web（一体）**：identity 与 identity-web 同部署绑定（登录页本就靠它），错误页样式统一 + 「返回应用」链接（需 client 注册信息）都指向前端渲染。后端只管「算 + 跳」。
- **局部 handler 覆盖全局**：分流逻辑落在浏览器类 Controller 自己的 `@ExceptionHandler`，语义清晰、机机类端点零感知，不把「是不是浏览器请求」的判定塞进全局 handler。
- **错误码语义合规**：infra 暂时不可用 = `temporarily_unavailable`(503)，与「client 配置错」`unauthorized_client`(400) 区分，不再误导排查。

## 后果

- **identity-web（跨仓，发 issue）**：新增 `/error` 路由 + 兜底页组件。
- **identity 本仓**：`AuthorizeController`/`LogoutController` 各加局部 `@ExceptionHandler`；`LogoutController` 失败分支改跳兜底页；`RemoteSsoClientRepositoryAdapter` 抖动降级路径改 `temporarily_unavailable`；新增配置 `identity.sso.error-page-url`。
- [CONTEXT.md](../../CONTEXT.md)「错误处理（OIDC 标准）」段同步修订为分流表述。
- #33 收口（A 浏览器兜底页 + B 错误码语义都在本 ADR 范围）。

## 关联

- [CONTEXT.md](../../CONTEXT.md)（错误处理 / app-registry 抖动降级）
- #33、#32（抖动降级引入）
