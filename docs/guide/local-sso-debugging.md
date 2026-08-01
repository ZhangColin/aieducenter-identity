# 本地 SSO 联调（.localhost，issue #16）

`.localhost` 浏览器自动解析到 127.0.0.1 且 HTTP 下即 secure context（Secure cookie 能种）——免改 hosts、免证书。

## 端口分工

| 地址 | 进程 | cookie 域 |
|---|---|---|
| `identity.localhost:10001` | identity 后端（IdP + dev 一键登） | identity.localhost（SSO cookie） |
| `demo.localhost:10010` | demo-backend（BFF） | demo.localhost（业务 cookie） |
| `demo.localhost:3000` | demo-web（Next.js，代理 `/api` `/auth` → :10010） | demo.localhost |

## 启动

```bash
./dev-up.sh                      # 起 PG+Redis，打印三进程命令
# 三个终端分别跑 identity / demo-backend / demo-web（见脚本输出）
```

## 验证首次登录 + 二次免登

1. 浏览器开 `http://demo.localhost:3000` → 未登录页（显示「登录」按钮）。
2. 点「登录」→ 跳 identity `/authorize`（无 SSO cookie）→ 跳 dev 一键登 → 自动登预置账号 `demo@aieducenter.com / demo12345` → 发 code → 回 demo `/auth/callback` → BFF 服务端换 token、种 `demo_session` → 回首页显示用户。
3. **二次免登**：清 demo 业务 cookie（或换无痕窗口重跑一次完整登录后），再点「登录」→ 这次 identity 有 SSO cookie → 直接发 code → 无感进入（没再登一次）。

## 常见排错

- **cookie 没带上**：确认地址是 `*.localhost`（不是 `localhost`），且 `Secure` cookie 在 HTTPS 之外只在 secure context 生效——`.localhost` 满足。
- **redirect_uri 不匹配**：identity local 白名单是 `http://demo.localhost:3000/auth/callback`（见 `application-local.yml`）；浏览器入口必须走 :3000（Next 代理）。
- **dev 一键登没触发**：确认 identity 起的是 `local` profile（`identity.sso.dev-login.enabled=true`、`login-page-url` 指向 dev-login）。
