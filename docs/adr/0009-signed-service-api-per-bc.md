# 对外签名服务 API 独立一层（按 bc 暴露，签名即准信）

identity 需对不接入 SSO 的应用提供用户服务 API（注册/登录/个人中心等），与平台其它服务共享同一套中央 Account。讨论中曾考虑作为特殊的「非 SSO 应用接入 / password grant / 放宽『应用不持有用户凭据』不变式」。

**决定**：作为普通签名服务——account + verification 各自在自己 bc 下暴露 `@RequireSignature` 端点（`/api/account/*` `/api/verification-codes/*` `/api/captchas/*`），与 sso 浏览器闭环（`/api/sso/*`）、OIDC 协议端点（根）三层 namespace 物理分明。签名即准信：任何持 apiKey/apiSecret 的调用方（admin bff = 业务应用 = SSO 应用做管理操作）一视同仁，不约束消费方用法。登录只回 `SubjectView`、不发 token、不建 identity 侧会话。

**为什么不是 token 路径**：平台下游服务（payment / wechat 等）走机机签名，没有一排「验 identity access_token 的资源服务器」在等 token；token 发给非 SSO 应用也只是应用自存自用，徒增 OIDC 三件套复杂度，且与「不接入 SSO」（不要 OIDC 产物）的初衷矛盾。

**为什么不是特殊叙事**：签名机机是平台既定模式（cartisan-openapi `@RequireSignature`，payment / wechat 先例，identity 的 `apikey-service-url` 已配），应走标准件。把通用「暴露服务 API」包装成特殊的「非 SSO 应用接入 / password grant」是反模式——回归「普通服务」视角后，「应用不持有用户凭据」不变式的 scope 自然限定为 SSO 浏览器链路（终端用户密码只在 identity-web 表单提交），不约束签名服务调用（传 password 验密是正常服务参数）。

**后果**：
- 非 SSO 应用会话自管、跨应用登出 / 会话一致性非本服务职责。
- 下游认用户靠调用方签名代调（「信调用方的话」）；「调用方能否代表某 userId」的 per-app 权限本期不做（#56 含操作白名单起步），推后整体设计。
- namespace 三层定型，未来新 bc 对外能力按同模式（自己 bc 下 `@RequireSignature`）。

落地：#55（namespace 迁移，腾空 `/api/account/*`）→ #56（签名服务 API）。
