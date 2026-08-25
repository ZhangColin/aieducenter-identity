# ADR-0012: 跨上下文 wire 错误码串——下沉 shared 契约单点持有

- 状态：已定
- 日期：2026-08-25
- 修订：[ADR-0007](0007-cross-context-via-appservice.md)「sso 自有码不引 verification domain 错误」的**常量归属**部分——串的所有权重申，防枚举与 wire 契约不变量不变。
- 关联：#76（本决策落地 + 启动修复）、#54（当初 SsoAuthError 立项，被本决策取代的归属表述）。

## 背景

cartisan-boot `470f787`（8/22）引入 `CodeMessageRegistry`：启动期全应用扫描 CodeMessage 枚举，**同 code 串必须映射同一常量**（防语义二义——`@ErrorCodes` 渲染 swagger 时 `require(code)` 必须无歧义解析）。SNAPSHOT 刷新后首次启动即失败。

冲突串 `VERIFICATION_CODE_INVALID` 当初即**双常量同串**（#54 / ADR-0007）：

- `verification.domain.error.VerificationCodeError.CODE_INVALID`——验码失败直抛，经 sso / account 的 ACL port 以 `DomainException` **透传**；
- `sso.domain.error.SsoAuthError.CODE_INVALID`——sso 自家抛出点（register 必填码兜底、login-code 账号不存在**翻译**），刻意复制同串以保「前端契约无感 + 防枚举（透传串 == 翻译串）」。

该串的对外出口天然跨上下文（sso register / login-code、account 验码透传），不是任何单一上下文的私有物——任一侧改串都会破坏 wire 契约或使防枚举两分支分叉。

## 决定

**跨上下文共享的 wire 错误码串，收归 `shared.error.SharedErrorCode` 单点持有，各上下文共引；上下文私有错误码仍留自家 `domain.error` 枚举。**

- `SharedErrorCode.VERIFICATION_CODE_INVALID` 是唯一常量；`VerificationCodeError` 删 `CODE_INVALID`、`SsoAuthError` 整体删除（仅此一常量）。
- wire 契约逐字符不变（#54「前端契约无感」延续）。
- 防枚举不变量（「错码」与「账号不存在」同 code + message + status）由「同串两处定义」升级为**同串同对象**——编译期保证，不再靠逐字符对齐。

## 与 ADR-0007 的关系

ADR-0007 禁的是跨上下文引用**对方 domain** 错误（sso→`verification.domain.error.*` 形成上下文耦合）。本决策不是回退：双方共引**中立契约位置**（shared），不产生上下文间依赖边——语义上该串本就是应用级对外契约，与 `CodeMessage` 接口本身（cartisan-core）同级。判定尺子：**串被几个上下文的对外出口使用——一个→留自家 `domain.error`；多个→`SharedErrorCode`**。

## 后续

同批框架强制（signed 端点 `@Operation` + `@ErrorCodes` 文档化）另行处理：#77。
