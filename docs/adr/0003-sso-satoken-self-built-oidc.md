# ADR-0003: SSO 实现 = Sa-Token 自搭 OIDC

> **⚠️ 部分修订（2026-07-31）**：本 ADR 的「对外 OIDC 端点自搭」仍有效；但「**会话/登录继续用 Sa-Token**」一条被 [ADR-0004](0004-drop-satoken-single-sso-session.md) 取代——identity 弃 sa-token，改一套 SSO 会话 + token 归 `/token`。下方相关表述保留作历史记录。

- 状态：已定（方向）；oauth2 模块覆盖度为实现期评估项
- 日期：2026-07-28

## 背景
平台已定「对外 OIDC 契约」（`/authorize` `/token` `/userinfo` `/jwks` `/discovery` `/logout`）+ token=JWT（[ADR-0002](0002-token-format-jwt.md)）。SSO 实现三选：
- ① Sa-Token 上自搭 OIDC 端点（会话引擎继续 Sa-Token）；
- ② `sa-token-sso` 模块（非标准 OIDC，与「对外 OIDC」冲突，基本排除）；
- ③ Spring Authorization Server（标准 OIDC 开箱即用，但自带一套 Spring Security 会话/client 模型，与 Sa-Token 两套会话要桥接、最重）。

## 决策
选 **① Sa-Token 自搭 OIDC 端点**。会话/登录继续用 Sa-Token（一套会话系统），自写 `/authorize` `/token` `/userinfo` `/jwks` `/discovery` `/logout`，自签 JWT、维护 jwks 公钥集。

## 理由
- 一套会话系统——不引入 SAS 的第二套会话 + 桥接复杂度。
- 团队熟 Sa-Token、velocity 高（对上架构「内部会话/token 引擎倾向 Sa-Token」）。
- 端点逐个遍历（authorize/token/userinfo/jwks/discovery/logout + 各核心参数）**未发现 Sa-Token 阻断点**。
- OIDC 端点是规范明确、有限的活，对内部平台 IdP（client 可控）自维护合规可接受。

## 风险 / 实现期评估项
- **Sa-Token 自带 oauth2 模块对完整 OIDC 的覆盖度**——够用就复用、不够手写补端点（到写代码时摸）。
- **自维护 OIDC 合规**：PKCE、nonce、jwk 轮换、开放重定向防护、各项参数安全校验，需照规范仔细实现（坑在安全细节）。
- 升级位：若评估后发现 Sa-Token oidc 覆盖太薄、自写成本超预期，再回方案 ③（Spring Authorization Server）。

## 关联
[ADR-0002](0002-token-format-jwt.md) · [CONTEXT.md](../../CONTEXT.md) OIDC 端点 · 平台 [architecture.md §6.1](../../../../aieducenter-architecture/docs/architecture.md)
