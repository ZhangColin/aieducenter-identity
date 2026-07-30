# aieducenter-identity

Gateway 网关服务，基于 cartisan-boot 框架。

## 核心文档

- [cartisan-boot 使用手册](docs/guide/cartisan-boot-使用手册.md) — 框架能力清单、API 文档和使用示例
- [限界上下文代码编写规范](docs/guide/限界上下文代码编写规范.md) — DDD 六边形架构落地指南

## 引用的 cartisan-boot 模块

- `cartisan-core` — DDD 基础类型、异常体系、架构注解、RequestContext
- `cartisan-web` — 统一响应体、全局异常处理、请求上下文、防重提交
- `cartisan-data-jpa` — BaseRepository、事件发布、审计、软删除
- `cartisan-openapi` — 服务间签名验证、API Key 管理
- `cartisan-security` — Sa-Token 认证集成、权限注解
- `cartisan-test` — ArchUnit 规则、测试基类

## 常用命令

- 编译：`mvn compile`
- 单元测试：`mvn test`
- 打包：`mvn package -DskipTests`
- 变异测试：`mvn org.pitest:pitest-maven:mutationCoverage`

## Agent skills

### Issue tracker

问题/工单记录为 GitHub Issues（使用 `gh` CLI）。详见 `docs/agents/issue-tracker.md`。

### Triage labels

沿用默认的五个 triage 标签（needs-triage / needs-info / ready-for-agent / ready-for-human / wontfix）。详见 `docs/agents/triage-labels.md`。

### Domain docs

single-context 布局：根目录 `CONTEXT.md` + `docs/adr/`。详见 `docs/agents/domain.md`。


## 平台架构上下文
本服务（identity）是平台终端用户身份基座 / 集中式 IdP（基础能力层），被所有业务应用消费。完整架构与决策在兄弟仓库 ../aieducenter-architecture/（起步包 docs/starters/identity.md）。

稳定不变式（务必遵守）：
- Operator 不在本服务（运营人员认证+角色/部门/岗位在统一后台 admin）——别把运营相关往这塞。
- SSO 走 OIDC（/authorize /token /userinfo /jwks /discovery）；token 只存服务端，浏览器不接触 token。
- 应用不持有用户凭据；社交登录（微信/Google/Apple）归本域统一接入、归一到中央 Account。
- tenantId 全程可空（C 端无感，无租户按 userId 隔离）；写侧需自带租户过滤（cartisan-boot 写侧无自动过滤）。
- 鉴权机器用 cartisan-security（共享库、不建用户表）——本服务自带 account 凭据表、实现 authenticate()/StpInterface；只 Account 一种身份，Sa-Token 默认 loginType 即可。
- 管组织治理角色（owner/admin/member）；应用内业务角色归应用自管，不在本服务。

深度（SSO 时序、为什么 OIDC/BFF、术语、决策记录）：读架构仓库 integration-flows.md §1、architecture.md §6.1、CONTEXT.md、map.md。
本项目自己的设计演进 → 本项目的 CONTEXT.md + docs/adr/。