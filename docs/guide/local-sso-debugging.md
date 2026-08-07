# 本地 SSO 联调（.localhost，issue #16/#27）

`.localhost` 浏览器自动解析到 127.0.0.1 且 HTTP 下即 secure context（Secure cookie 能种）——免改 hosts、免证书。

## 端口分工

| 地址 | 进程 | cookie 域 |
|---|---|---|
| `identity.localhost:10001` | identity 后端（IdP） | identity.localhost（SSO cookie） |
| `identity.localhost:10002` | identity-web（统一登录页，Next dev，rewrite `/api/*` → `127.0.0.1:10001`） | identity.localhost（共享 SSO cookie） |
| `demo.localhost:10010` | demo-backend（BFF） | demo.localhost（业务 cookie） |
| `demo.localhost:3000` | demo-web（Next.js，代理 `/api` `/auth` → :10010） | demo.localhost |

SSO cookie 是 host-only（`identity.localhost`），:10001 与 :10002 跨端口共享，所以 identity-web 登录种 cookie 后 identity 后端认。

## 启动

```bash
./dev-up.sh                      # 起 PG+Redis，打印四进程命令
# 四个终端分别跑 identity / identity-web / demo-backend / demo-web（见脚本输出）
```

## 验证首次登录 + 二次免登

1. 浏览器开 `http://demo.localhost:3000` → 未登录页（显示「登录」按钮）。
2. 点「登录」→ 跳 identity `/authorize`（无 SSO cookie）→ 302 到 identity-web 登录页（透传 `client_id`/`redirect_uri`/`state`）→ 用预置账号 `demo@aieducenter.com / demo12345` 登录 → 种 SSO cookie → 跳回 `/authorize` 发 code → 回 demo `/auth/callback` → BFF 服务端换 token、种 `demo_session` → 回首页显示用户。
3. **二次免登**：清 demo 业务 cookie（或换无痕窗口重跑一次完整登录后），再点「登录」→ 这次 identity 有 SSO cookie → 直接发 code → 无感进入（没再登一次）。

## 验证登出（issue #38）

点「登出」→ demo-backend 清本地 `demo_session` + BFF 会话 → **302 到 identity `/logout`**（`client_id` + `post_logout_redirect_uri=http://demo.localhost:3000/` + `state`）→ identity 清 SSO 会话 + `sso_session` cookie → 302 回 demo 首页。

- **再登录需重新输密码**：登出后再点「登录」→ identity `/authorize` 无 SSO cookie → 走完整登录页（不再二次免登）。修复前登出只清 demo 本地会话、identity 侧 `sso_session` 仍在，故下次登录直接发 code（不输密码）。
- **devtools**：登出后 identity 域（`identity.localhost`）的 `sso_session` cookie 应被清（maxAge=0）。
- **post_logout_redirect_uri 白名单（前置）**：identity 用 `redirect_uri` 同集做精确匹配校验登出回跳（`SsoLogoutAppService`）。demo 现登记的 `redirect_uri` 是 `http://demo.localhost:3000/auth/callback`；登出后回 demo 首页需把 `http://demo.localhost:3000/` 一并登记进 demo client 的 redirect_uri 白名单（app-registry，admin 控制台或 SSO bootstrap），否则 identity 清完会话返 200 不跳转——落 identity 空白页（但 SSO 会话已清，再登录仍要求密码）。

## dev-login 手工兜底

`identity.sso.dev-login.enabled` 保持开启，预置账号照常种入，但 dev 一键登**不再是** login-page-url 默认。identity-web 没起、又想先跑通 SSO 链时，浏览器直接访问：

```
http://identity.localhost:10001/api/auth/dev-login?client_id=<client>&redirect_uri=<回调>&state=<state>
```

（参数从 /authorize 302 的 Location 里抄）→ 自动登预置账号、种 SSO cookie、发 code 回 redirect_uri，链路续走。

## 常见排错

- **cookie 没带上**：确认地址是 `*.localhost`（不是 `localhost`），且 `Secure` cookie 在 HTTPS 之外只在 secure context 生效——`.localhost` 满足。
- **redirect_uri 不匹配**：identity local 白名单是 `http://demo.localhost:3000/auth/callback`（见 `application-local.yml`）；浏览器入口必须走 :3000（Next 代理）。
- **/authorize 302 到登录页但打不开**：确认 identity-web 起了（`identity.localhost:10002`）；没起就用上面的 dev-login 手工兜底。
- **登出后落 identity 空白页**：identity `/logout` 清完 SSO 会话但没 302 回 demo → `post_logout_redirect_uri`（`http://demo.localhost:3000/`）不在 demo client 的 redirect_uri 白名单（app-registry）。把它加进白名单即可（见上「验证登出」）。
