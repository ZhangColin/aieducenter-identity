# ADR-0001: Account 凭据模型（用户表内联 + 扩展表 + external_identities）

- 状态：已定
- 日期：2026-07-27

## 背景
本服务是平台集中式 IdP，要支持密码 / 邮箱验证码 / 手机验证码 / 社交（微信/Google/Apple）多种登录方式，且**社交登录要归一到同一个用户**（不割裂）。讨论了两种模型：
- 「userId 锚点 + 凭据集合」（凭据是 Account 上一组可绑可解绑的因子，独立表）；
- 「用户表内联凭据」（email/phone/password 是用户表字段 + 个人信息扩展表 + 第三方绑定表）。

## 决策
采用**内联模型**，三张表：
- **`account`**：`userId`（PK，TSID，唯一稳定身份，作 SSO `sub`）+ 登录定位字段 `email`（唯一、可空）/ `phone`（唯一、可空）/ `password_hash`（可空）+ 状态字段（状态、锁定、最后登录等）。
- **`profile`**（扩展表，1:1）：昵称、头像等个人资料，不塞进用户主表。
- **`external_identities`**：`provider`（微信/Google/Apple）+ `provider_uid`（unionid/openid）+ `userId`，第三方登录靠它定位用户、归一到同一 Account。

`userId` 是唯一稳定身份；email/phone 是「登录入口 + 联络通道」、不是身份本体，换了它们 `userId` 不变。

## 结果
- **好**：务实、贴合 studio 现状、迁移改动小；email/phone 全局唯一便于定位用户；社交归一到 `userId` 清晰。
- **与 studio 的差异**：① **丢掉 `username` 登录键**（studio 的 NOT NULL + 正则不搬）——注册不要 username，email/phone 作登录定位、`userId` 作锚点，显示名走 `profile.nickname`；② **`password_hash` 改可空**（社交账号无密码）。
- **约束**：不支持「一个账号绑多个同类型凭据」（如多手机号、多 passkey）——将来真要再加表。
- **本期不做 MFA**（见待决议题；除非未来提出特别需求）。

## 关联
[CONTEXT.md](../../CONTEXT.md) 术语表 · 平台 [architecture.md §6.1](../../../../aieducenter-architecture/docs/architecture.md)
