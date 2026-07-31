# ADR-0004: 弃 sa-token / 一套 SSO 会话 / token 归 /token

- 状态：已定
- 日期：2026-07-31
- 修订：[ADR-0003](0003-sso-satoken-self-built-oidc.md)「会话引擎继续 Sa-Token」一条

## 背景

[ADR-0003](0003-sso-satoken-self-built-oidc.md) 定了「SSO 实现 = Sa-Token 自搭 OIDC」，会话引擎继续 Sa-Token。#11/#12/#13 据此实现：access JWT 兼作 Sa-Token 会话 token、`@RequireAuth` 鉴权、login 接口签 token 返回。

重新审视 identity 这个 IdP 的本质后，发现 **sa-token 在这里是错配**：

- cartisan-security / sa-token 的甜区是「**一个应用的前端，持 token 调它自己后端 API**」的鉴权（企业后台场景）。
- identity 是 **IdP，不是消费应用**。它的接口分四类，**没一类**需要 sa-token 的 token-match 鉴权：

| 接口类 | 例子 | 该怎么认人 | sa-token 适用？ |
|---|---|---|---|
| 公开 | register / login / 验证码 / reset-password | 无鉴权 / 凭验证码 | 否 |
| SSO 会话内 | me / profile / 改密 / 登出 | **SSO cookie 会话** | 否（sa-token 是 header-token，非 cookie-session） |
| 机机 | 用户数据 / 用户管理 API | **app-registry apikey 签名** | 否 |
| OIDC 协议 | /authorize /token /userinfo /jwks /discovery | **协议本身**（code、access JWT 验签） | 否 |

- 「access JWT 兼 Sa-Token 会话 token」+「login 签 token 返回」是**消费应用惯性**（从 studio 迁来）。SSO 模式下，token 该由 `/token` 端点签发给**消费方 BFF**，不在 login；identity 自己的受保护接口凭 **SSO cookie 会话**认人。
- 这正是「两套会话」怪状的根源：sa-token 那份短命 access-JWT 索引本不该存在。

## 决策

1. **弃 sa-token / cartisan-security 鉴权**：identity 不用 `@RequireAuth` / `StpInterface` / `SecurityFilter` 的 token-match 链路。
2. **一套 SSO 会话**：identity 唯一用户会话 = IdP SSO 会话（cookie + Redis + 双超时 30/90 天）。受保护接口凭 SSO cookie 会话认人（自写一个会话拦截器替代 `@RequireAuth`，从 cookie 读 sessionId → 查 Redis → 填 RequestContext）。
3. **token 是 OIDC 产物，归 `/token` 端点**：access/id/refresh 在 `/token`（code 换 token）签发给消费方 BFF；`login`/`register` 只建 SSO 会话 + 发 code，**不签 token 返回**。
4. **#11/#13 的 JWT 签名、refresh 轮换逻辑保留**，从 login/refresh 接口摘出，归 `/token` 端点复用。

## 理由

- **概念正确**：IdP 的会话 = SSO 会话（长命、cookie、给 /authorize 判免登）；token = 给消费方的 OIDC 凭证（短命、BFF 服务端、给调资源）。两者职责分明，**不是两套重复会话**。
- **不过度设计**：四类接口各走各的认人方式（cookie / apikey / 协议），不硬套消费应用的 sa-token 模式。
- **可撤销性**：SSO 会话自存 Redis，登出 / 改密 / 封号按 userId 清会话即可；access JWT 短命（15min）自然过期，无需维护作废名单。
- **顺手消除「两套会话」**：之前为不动已有代码妥协出的「两条腿」（sa-token 短命索引 + 独立 SSO 会话）不必再有。

## 后果

- 回退 #11/#12/#13 的「access JWT 兼 Sa-Token 会话 token」「login 签 token 返回」「@RequireAuth 链路」「`/api/account/refresh` 端点」。
- 受保护接口（me / profile / 登出 / 改密）改凭 SSO cookie 会话；新增一个会话拦截器填 RequestContext。
- token 三件套的签发从 login 挪到 `/token`（Phase 2）；JWT 签名 / refresh 轮换核心代码复用、不重写。
- 架构仓库 `map.md` 与 `architecture.md §6.1 ②` 的「内部会话/token 引擎倾向 Sa-Token」是雾区残留，建议回写修订（待与架构侧确认）。

## 关联

- [ADR-0003](0003-sso-satoken-self-built-oidc.md)（OIDC 端点仍自搭，仅「会话引擎继续 Sa-Token」被本 ADR 取代）
- [CONTEXT.md](../../CONTEXT.md)
- 架构仓库 `map.md`、`architecture.md §6.1`、`integration-flows.md §1`
