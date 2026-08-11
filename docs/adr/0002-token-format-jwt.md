# ADR-0002: token 形态 = JWT（id_token + access_token）

- 状态：已定
- 日期：2026-07-27
- 注：本 ADR 定 token **格式**（JWT），与归属无关；token 代码归属 sso 上下文见 [ADR-0008](0008-token-ownership-sso-account-user-api.md)

## 背景
OIDC 规范涉及三种 token：`id_token`（规范要求 JWT）、`access_token`（格式可选）、`refresh_token`（总是不透明串、服务端存）。`access_token` 有两种格式：
- **JWT**：自包含、带签名，对接方用 IdP 公钥（`/jwks`）本地验、不用回 IdP；
- **opaque**：不透明串、服务端存、必须回 IdP 验/查（最接近 studio 现状的 opaque UUID）。

## 决策
**`id_token` + `access_token` 都用 JWT**；对接方用 `/jwks` 公钥本地验；`refresh_token` 不透明串、服务端存。access_token 短有效期（如 15 分钟）+ refresh_token 续命。

## 结果
- **好**：对接方后端本地验 token、不用每次回 IdP → 性能好、IdP 非强依赖（IdP 短暂不可用时已发 token 照验）；OIDC 主流做法、标准库直接支持。
- **坏**：access_token 不可立即撤销——封号/登出不会让已发的 token 立刻失效，靠短有效期自然过期。
- **缓释**：BFF 模式下 token 只存服务端、浏览器不接触，配合短有效期 + refresh，不可撤销的风险可控。
- **升级位**：若将来业务强要「立即失效」（封号/登出实时），引入 token 黑名单或 `tokenVersion` 字段（用户登出/封号即递增，旧 token 失效），不必改 token 格式。

## 关联
[CONTEXT.md](../../CONTEXT.md) 术语表 · BFF 模式（token 只存服务端，见平台 architecture.md §6.1.④）
