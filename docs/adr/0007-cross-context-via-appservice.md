# ADR-0007: 跨上下文调用只走被调方 AppService（服务级 DDD 原则）

- 状态：已定
- 日期：2026-08-11
- 落实：ArchUnit 守护规则拟推 cartisan-boot 框架（见「结果」），本服务不加本地副本

## 背景
本服务有两个限界上下文：`account`（终端用户身份基座 / 凭据权威）与 `sso`（SSO 会话 + OIDC 闭环）。ADR-0004 落地后，sso 为了「能跑」直接 reach 进 account 的 domain 层——注入 `AccountRepository` / `ProfileRepository`、拿 `Account` / `Profile` 聚合、调 `AccountPasswordEncoderService` 域服务、引 `account.domain.token.*` 端口、抛 `AccountError`。除 `SsoTokenAppService → TokenIssuerAppService` 一处走 AppService 外，sso→account 的十余条边全是 domain 直穿（见 #48 审计）。

这违反 DDD 限界上下文边界：上下文之间应只通过**应用层（AppService）**交往，不应直接耦合对方的 domain（聚合 / 仓储 / 域服务 / 域错误）。直穿 domain 的代价：上下文边界名存实亡、对方 domain 内部重构会跨上下文炸、account 无法独立演进。

## 决策
**本服务级原则：一个上下文调用另一个上下文，只能调对方的 AppService（应用层）。** 禁止：
- 跨上下文注入对方 domain 仓储（`*Repository`）；
- 跨上下文引用对方 domain 聚合 / 值对象；
- 跨上下文调对方 domain 服务（`*DomainService`）；
- 跨上下文引对方 domain 错误码 / domain 端口（domain 端口随其所属上下文走，不作跨上下文共享契约）。

允许的跨上下文形态只有：**应用层 → 应用层**——调用方 AppService 注入被调方 AppService，经被调方 AppService 的 DTO 交换数据。

**方向不对称的例外（ACL port，仅断环用）：** 当依赖方向「逆流」（下游消费方反过来要通知上游，如 account 改密 / 封号要销毁 sso 会话），用**防腐层端口**（调用方上下文定义 port、被调方上下文出 adapter）打破 account↔sso 循环。这是 port/ACL 模式在本服务的唯一正当用途——**断环**，不是常规跨上下文调用。正向（sso→account）一律直接 AppService 注入，不起 port。

## 理由
- **边界真存在**：只走 AppService = 每条跨上下文依赖都经一个明确、可演进的契约面，对方 domain 内部重构不炸调用方。
- **account 可独立演进**：account 是平台用户一体化基座（见 ADR-0008），其 domain 模型要能自由重构（加字段、拆聚合、换实现），不被 sso 的十余处直穿焊死。
- **不对称有据**：sso→account 是自然顺流（sso 消费 account），直接注入最轻；account→sso 是逆流通知，port 断环是 DDD 标准解。两条边形态不同是因为性质不同，不是不一致。
- **port 不滥用**：port/ACL 是跨服务边界级别的重手段，单服务内顺流方向不值得；只在断环时用。

## 结果
- **好**：上下文边界成为可执行契约，account domain 可独立重构，sso 对 account 的依赖收敛到几个 AppService 面。
- **代价**：account 需补齐被 sso 消费的 AppService 表面（`AccountAuthAppService` / `AccountSubjectAppService`，见 ADR-0008）。
- **守护**：ArchUnit 规则（X 上下文只能依赖 Y 上下文 `application` 层；ACL adapter 作 sanctioned 例外 carve out）拟作为通用规则推给 cartisan-boot 框架（`CartisanArchRules`），所有 cartisan-boot 服务共享；本服务**不加本地副本**，待框架落地后继承。框架落地前，结构正确性靠 PR 时 grep 核验。

## 关联
- [ADR-0008](0008-token-ownership-sso-account-user-api.md)（本原则在 token 归属重划 + account 用户一体化缝上的具体落实）
- [ADR-0004](0004-drop-satoken-single-sso-session.md)
- #48（本次重构）、#47（decoder 直穿，随本次消解）
- `docs/guide/限界上下文代码编写规范.md` §2.2
