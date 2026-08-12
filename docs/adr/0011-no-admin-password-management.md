# ADR-0011: 后台不处理终端用户密码（修订 ADR-0010 范围）

- 状态：已定
- 日期：2026-08-12
- 修订：[ADR-0010](0010-admin-management-api.md) 把「清密码 / 强制改密」列为后台管理能力——本决策把这两项**移出范围**。
- 关联：#71（admin 无码重置 + 清密码）、#72（强制改密 passwordMustChange + 登录 enforcement）—— 均 wontfix 关闭。

## 背景

[ADR-0010](0010-admin-management-api.md) 立项时，把「封号 / 解封 / 解锁 / **清密码** / **强制改密** / 踢人 / 审计」一并作为后台管理（admin-console）的能力清单，#71 / #72 据此排期。#71（管理员强制重置密码 + 清除密码）已实现并通过全量测试后，复核认定：**后台（admin 侧）unilateral 处理终端用户密码不合规则**——管理员不应能替用户设置、清除、或强制变更密码。该结论同样覆盖 #72 的强制改密（admin 置 `passwordMustChange` 标志驱动用户改密，仍是「后台驱动密码变更」，同类）。

## 决定

**identity 后台（admin-console）不提供任何处理终端用户密码的能力。** 不暴露 admin 侧的密码重置、密码清除、强制改密——设置 / 清除 / 强制变更密码三类操作一律不做。

终端用户密码改动只走**用户自助**路径（用户掌握自己的密码，admin 不插手）：

- 验证码重置：`POST /api/account/reset-password`（凭 identifier + 验证码，签名服务）/ 对应 SSO 浏览器闭环。
- 验旧密改密：`POST /api/account/{userId}/change-password`（凭旧密码）。

非密码类管理操作**不受影响**、照常提供：封号 `disable` / 解封 `activate` / 解锁 `unlock` / 踢人 `sessions/revoke` / 用户搜索 / 管理详情。这些是账号状态与会话治理，不触碰密码。

## 移出范围（据此 wontfix）

- **#71**：管理员无码重置密码（`POST /{userId}/reset-password`）+ 清密码（`POST /{userId}/clear-password`）。已实现后整体回退（代码 `git reset` 撤销、未推送）。
- **#72**：强制改密（`passwordMustChange` 字段 + `force-change-password` 端点 + 登录 enforcement）。未实现即关闭。

> 相关领域原语（`Account.resetPassword` / `changePassword`）仍保留——它们服务于自助路径，非 admin 路径。本 ADR 砍的是「admin 侧入口与 admin 驱动的密码变更」，不是这些自助原语本身。

## 为什么不做「后台改密」而靠自助

合规层面：后台持能力 unilateral 改用户密码 = 凭据完整性风险面（谁能改、何时改、是否留痕都成审计/合规问题）。用户自助（验证码 / 旧密码）把「改密」控制权留在用户手里，admin 只管账号状态（封号/解封等非密码治理）。这是范围收缩，不是能力缺失——封号已能处置违规账号，踢人已能处置疑似盗号，不需要再叠加后台改密。
