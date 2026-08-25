package com.aieducenter.aieducenteridentity.account.endpoints.controller;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.aieducenter.aieducenteridentity.account.application.AccountAuthAppService;
import com.aieducenter.aieducenteridentity.account.application.AccountManagementAppService;
import com.aieducenter.aieducenteridentity.account.application.AccountPasswordAppService;
import com.aieducenter.aieducenteridentity.account.application.AccountProfileAppService;
import com.aieducenter.aieducenteridentity.account.application.AccountSubjectAppService;
import com.aieducenter.aieducenteridentity.account.application.dto.command.AuthenticateByCodeCommand;
import com.aieducenter.aieducenteridentity.account.application.dto.command.AuthenticateCommand;
import com.aieducenter.aieducenteridentity.account.application.dto.command.ChangePasswordCommand;
import com.aieducenter.aieducenteridentity.account.application.dto.command.DisableAccountCommand;
import com.aieducenter.aieducenteridentity.account.application.dto.command.ManagementReasonCommand;
import com.aieducenter.aieducenteridentity.account.application.dto.command.RegisterByCodeCommand;
import com.aieducenter.aieducenteridentity.account.application.dto.command.ResetPasswordByCodeCommand;
import com.aieducenter.aieducenteridentity.account.application.dto.command.ResetPasswordCommand;
import com.aieducenter.aieducenteridentity.account.application.dto.command.UpdateProfileCommand;
import com.aieducenter.aieducenteridentity.account.application.dto.query.AccountSearchQuery;
import com.aieducenter.aieducenteridentity.account.application.dto.response.AccountManagementView;
import com.aieducenter.aieducenteridentity.account.application.dto.response.SubjectView;
import com.aieducenter.aieducenteridentity.account.endpoints.web.RequireManagementCaller;
import com.cartisan.openapi.annotation.RequireSignature;
import com.cartisan.web.doc.ErrorCodes;
import com.cartisan.web.response.ApiResponse;
import com.cartisan.web.response.PageResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * 账号签名服务端点——{@code /api/account/*}（CONTEXT「签名服务 API」/ ADR-0009 / #56）。
 *
 * <p>薄壳 adapter：签名 gate（{@code @RequireSignature}，cartisan-openapi 拦截器 + RemoteApiKeyProvider
 * 解析调用方）→ 委托本 bc 的 {@link AccountAuthAppService} / {@link AccountPasswordAppService}，
 * <b>不重写、不重测领域逻辑</b>。登录只回 {@link SubjectView}（不发 token、不建 identity 侧会话——
 * 「登录态 / session」是调用方自己的概念）。cookie / public 自助端点已在 {@code /api/sso/*}
 * （{@code SsoAccountController}），本 namespace 专给签名服务。</p>
 *
 * <p>类级 {@code @RequireSignature}：本 controller 所有端点皆需签名（拦截器取方法注解兜类注解）。
 * 错误响应走标准 cartisan-web {@link ApiResponse}（非 OIDC {@code {error, error_description}}）；
 * 缺失 / 错签名由框架返回 401。</p>
 *
 * <p><b>错误码契约</b>（#77，cartisan-web 470f787）：每端点 {@code @ErrorCodes} 声明其调用链
 * 可抛的业务错误码，渲染进 swagger description（与 CodeMessage 枚举单点同源——错误码不手写进文案，
 * 防随注册表演进漂移）。范围约定：<b>只列业务码</b>（{@code DomainException}/{@code ApplicationException}
 * 的 code 串）；401/403（签名 gate / 管理白名单 gate——拦截器直写、无注册 code 串）与通用参数校验
 * （{@code BAD_REQUEST} 系框架统一响应）对所有 signed 端点共有，不逐端点声明；调用链上不可达的码不列
 * （如 BCrypt encode 恒非 null，{@code PASSWORD_WEAK} 在本 namespace 不可触）——但跨层同义校验
 * （验码侧 / 聚合侧各一套格式正则）按并集列，防两套演进漂移后契约缺码。无业务错误码的端点
 * 声明 {@code INTERNAL_SERVER_ERROR} 占位（仅基建错可中断），description 注明。声明序 = 调用链发生序
 * （验码 → 定位 → 领域不变量）。</p>
 *
 * <p>落地分期：#58 打通骨架 + {@code authenticate}（tracer bullet）；#59 加按 identifier 三端点
 * （{@code authenticate-by-code} / {@code register} / {@code reset-password}）；#60 加按 userId / identifier
 * 读三端点（{@code GET {userId}} / {@code GET {userId}/profile} / {@code GET /find}）；#61 加按 userId 写两端点
 * （{@code PUT {userId}/profile} / {@code POST {userId}/change-password}）——签名路径下 {@code RequestContext.getUserId()}
 * 为 null，故走 AppService 的 userId 参数版本（非 cookie 版 {@code updateCurrentProfile} / {@code changePassword}）。
 * #67 起后台管理（admin-console）端点加 {@code @RequireManagementCaller} 白名单 gate：#67 管理详情读、
 * #68 封号（{@code POST {userId}/disable}，reason 必填）、#69 解封 / 解锁 / 独立踢人（{@code activate} /
 * {@code unlock} / {@code sessions/revoke}，reason 可选）；#70 用户搜索（{@code GET /api/account}，分页多条件，
 * 首次启用 Specification/Pageable，只读不审计）。</p>
 *
 * @since 0.1.0
 */
@RestController
@RequestMapping("/api/account")
@RequireSignature
@Validated
@Tag(name = "Account / Signed", description = "账号签名服务（机机，@RequireSignature）")
public class SignedAccountController {

    private final AccountAuthAppService authAppService;
    private final AccountPasswordAppService passwordAppService;
    private final AccountProfileAppService profileAppService;
    private final AccountSubjectAppService subjectAppService;
    private final AccountManagementAppService managementAppService;

    public SignedAccountController(AccountAuthAppService authAppService,
            AccountPasswordAppService passwordAppService, AccountProfileAppService profileAppService,
            AccountSubjectAppService subjectAppService, AccountManagementAppService managementAppService) {
        this.authAppService = authAppService;
        this.passwordAppService = passwordAppService;
        this.profileAppService = profileAppService;
        this.subjectAppService = subjectAppService;
        this.managementAppService = managementAppService;
    }

    @PostMapping("/authenticate")
    @Operation(summary = "密码验密登录",
        description = "签名调用方凭 email/phone + 密码验密，返回 SubjectView（不发 token、不建会话）。"
            + "账号不存在与密码错统一回同一错误（防用户枚举）；停用 / 锁定在密码通过后才告知。")
    @ErrorCodes({"ACCOUNT_009", "ACCOUNT_010", "ACCOUNT_011"})
    public ApiResponse<SubjectView> authenticate(@Valid @RequestBody AuthenticateCommand command) {
        return ApiResponse.ok(authAppService.authenticate(command.identifier(), command.password()));
    }

    @PostMapping("/authenticate-by-code")
    @Operation(summary = "验证码登录",
        description = "签名调用方凭 email/phone + 验证码登录，返回 SubjectView（不发 token、不建会话）。"
            + "先验码（purpose=LOGIN，identifier 含 @ 走邮箱、否则手机，格式各验各的）再委托 "
            + "authenticateByIdentifier；码错 / 验码通过但账号不存在 / 停用 / 锁定 → 标准错误响应。")
    @ErrorCodes({"VERIFICATION_EMAIL_INVALID", "VERIFICATION_PHONE_INVALID",
        "VERIFICATION_CODE_INVALID", "VERIFICATION_CODE_EXPIRED", "VERIFICATION_CODE_ALREADY_USED",
        "ACCOUNT_012", "ACCOUNT_010", "ACCOUNT_011"})
    public ApiResponse<SubjectView> authenticateByCode(@Valid @RequestBody AuthenticateByCodeCommand command) {
        return ApiResponse.ok(authAppService.authenticateByCode(command.identifier(), command.code()));
    }

    @PostMapping("/register")
    @Operation(summary = "注册",
        description = "签名调用方凭 email 或 phone + 验过的码（+ 可选密码）注册到中央 Account，返回 SubjectView"
            + "（注册即登录，不发 token、不建会话）。先验码（purpose=REGISTER，提供的联络方式各验各的——"
            + "email 优先）再委托 register；码错 / 联络方式不变量 / 唯一性冲突 → 标准错误响应"
            + "（email 格式错在验码侧先抛，聚合侧格式校验兜 phone 及必填）。")
    @ErrorCodes({"VERIFICATION_EMAIL_INVALID", "VERIFICATION_PHONE_INVALID",
        "VERIFICATION_CODE_INVALID", "VERIFICATION_CODE_EXPIRED", "VERIFICATION_CODE_ALREADY_USED",
        "ACCOUNT_001", "ACCOUNT_002", "ACCOUNT_003", "ACCOUNT_007", "ACCOUNT_008"})
    public ApiResponse<SubjectView> register(@Valid @RequestBody RegisterByCodeCommand command) {
        return ApiResponse.ok(authAppService.registerByCode(command));
    }

    @PostMapping("/reset-password")
    @Operation(summary = "重置密码",
        description = "签名调用方凭 identifier + 验证码重置密码，委托 resetPassword（内部验码 purpose=RESET_PASSWORD + "
            + "改密 + 踢会话）。成功 → 204；码错 / 验码通过但账号不存在 → 标准错误响应。")
    @ErrorCodes({"VERIFICATION_EMAIL_INVALID", "VERIFICATION_PHONE_INVALID",
        "VERIFICATION_CODE_INVALID", "VERIFICATION_CODE_EXPIRED", "VERIFICATION_CODE_ALREADY_USED",
        "ACCOUNT_012"})
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordByCodeCommand command) {
        passwordAppService.resetPassword(new ResetPasswordCommand(
            command.identifier(), command.code(), command.newPassword()));
        return ResponseEntity.noContent().build();
    }

    // ========== #60：按 userId / identifier 读 ==========

    @GetMapping("/{userId}")
    @Operation(summary = "按 userId 取 subject",
        description = "签名调用方凭 userId 取 SubjectView（复用 subjectClaims——userId/email/phone/nickname/avatar/status）。"
            + "userId 无对应账号 → 404。不 gate 可用性——status 兜出、调用方自决。")
    @ErrorCodes("ACCOUNT_013")
    public ApiResponse<SubjectView> getSubject(@PathVariable Long userId) {
        return ApiResponse.ok(subjectAppService.subjectClaims(userId));
    }

    @GetMapping("/{userId}/profile")
    @Operation(summary = "按 userId 取 profile",
        description = "签名调用方凭 userId 取 SubjectView 作 profile——SubjectView 已含 nickname/avatar，"
            + "不另造 profile 读模型路径（与 GET /{userId} 同 payload，仅语义区分；错误契约亦同）。")
    @ErrorCodes("ACCOUNT_013")
    public ApiResponse<SubjectView> getProfile(@PathVariable Long userId) {
        return ApiResponse.ok(subjectAppService.subjectClaims(userId));
    }

    @GetMapping("/find")
    @Operation(summary = "按 email/phone 查 subject",
        description = "签名调用方凭 email 或 phone 查 SubjectView（email 优先）。命中 → SubjectView；"
            + "email/phone 都未填 → 400；未命中 → 404。纯读、不 gate、无副作用（畸形联络方式只是查不到、"
            + "落未命中，不另校格式）。")
    @ErrorCodes({"ACCOUNT_001", "ACCOUNT_013"})
    public ApiResponse<SubjectView> find(@RequestParam(required = false) String email,
            @RequestParam(required = false) String phone) {
        return ApiResponse.ok(subjectAppService.findSubject(email, phone));
    }

    // ========== #61：按 userId 写（profile / change-password）==========

    @PutMapping("/{userId}/profile")
    @Operation(summary = "按 userId 改 profile",
        description = "签名调用方凭 userId 改昵称/头像（仅更新非空字段）。成功 → 204。委托"
            + " updateProfile(userId, …)——签名路径下 RequestContext.getUserId() 为 null，故走 userId 参数版本"
            + "（非 cookie 版 updateCurrentProfile）。userId 无对应账号 → 404。")
    @ErrorCodes("ACCOUNT_013")
    public ResponseEntity<Void> updateProfile(@PathVariable Long userId,
            @Valid @RequestBody UpdateProfileCommand command) {
        profileAppService.updateProfile(userId, command);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{userId}/change-password")
    @Operation(summary = "按 userId 改密码",
        description = "签名调用方凭 userId + 旧密码改密码（验旧密、改后踢出所有会话）。成功 → 204；"
            + "旧密错 / 新旧同 → 400；userId 无对应账号 → 404。"
            + "委托 changePassword(userId, …)——签名路径下走 userId 参数版本（非 cookie 版 changePassword）。")
    @ErrorCodes({"ACCOUNT_013", "ACCOUNT_005", "ACCOUNT_006"})
    public ResponseEntity<Void> changePassword(@PathVariable Long userId,
            @Valid @RequestBody ChangePasswordCommand command) {
        passwordAppService.changePassword(userId, command);
        return ResponseEntity.noContent().build();
    }

    // ========== #67：后台管理（admin-console，@RequireManagementCaller）==========

    @GetMapping("/{userId}/management")
    @RequireManagementCaller
    @Operation(summary = "管理详情",
        description = "admin-console 签名调用方取账号管理全貌（status / locked / hasPassword + 资料），"
            + "区别于面向终端应用的 SubjectView——分列 status/locked、暴露 hasPassword。"
            + "@RequireManagementCaller 白名单 gate（默认 admin-console）：非白名单签名调用方 → 403；"
            + "未签名 → 401（签名 gate）。userId 无对应账号 → 404。")
    @ErrorCodes("ACCOUNT_013")
    public ApiResponse<AccountManagementView> getManagementDetail(@PathVariable Long userId) {
        return ApiResponse.ok(managementAppService.managementDetail(userId));
    }

    @PostMapping("/{userId}/disable")
    @RequireManagementCaller
    @Operation(summary = "封号（停用账号）",
        description = "admin-console 签名调用方封号（reason 必填）：状态置 DISABLED + 自动清该用户所有 SSO 会话（踢人）"
            + "+ 同步落审计 op_type=DISABLE（operator 取 RequestContext）。成功 → 204；reason 缺失 → 400（不办理）；"
            + "userId 无对应账号 → 404。@RequireManagementCaller 白名单 gate：非白名单签名 → 403；"
            + "未签名 → 401（签名 gate）。委托 AccountManagementAppService.disable（复用 AccountStatusAppService.disable）。")
    @ErrorCodes("ACCOUNT_013")
    public ResponseEntity<Void> disable(@PathVariable Long userId,
            @Valid @RequestBody DisableAccountCommand command) {
        managementAppService.disable(userId, command.reason());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{userId}/activate")
    @RequireManagementCaller
    @Operation(summary = "解封（激活账号）",
        description = "admin-console 签名调用方解封账号：状态置 ACTIVE（不改会话——封号时已清，用户需重新登录）"
            + "+ 同步落审计 op_type=ACTIVATE。成功 → 204；userId 无对应账号 → 404。"
            + "reason 可选（空 body 亦可）。@RequireManagementCaller 白名单 gate：非白名单签名 → 403；"
            + "未签名 → 401（签名 gate）。委托 AccountManagementAppService.activate（复用 AccountStatusAppService.activate）。")
    @ErrorCodes("ACCOUNT_013")
    public ResponseEntity<Void> activate(@PathVariable Long userId,
            @Valid @RequestBody(required = false) ManagementReasonCommand command) {
        managementAppService.activate(userId, ManagementReasonCommand.reasonOrNull(command));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{userId}/unlock")
    @RequireManagementCaller
    @Operation(summary = "解锁",
        description = "admin-console 签名调用方解锁账号：locked 置 false（解除系统自动锁定，不改会话）+ 同步落审计 "
            + "op_type=UNLOCK。后台只善后解锁（lock 临时锁定不暴露给后台，ADR-0010）。成功 → 204；"
            + "userId 无对应账号 → 404。reason 可选。@RequireManagementCaller 白名单 gate："
            + "非白名单签名 → 403；未签名 → 401。委托 AccountManagementAppService.unlock（复用 AccountStatusAppService.unlock）。")
    @ErrorCodes("ACCOUNT_013")
    public ResponseEntity<Void> unlock(@PathVariable Long userId,
            @Valid @RequestBody(required = false) ManagementReasonCommand command) {
        managementAppService.unlock(userId, ManagementReasonCommand.reasonOrNull(command));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{userId}/sessions/revoke")
    @RequireManagementCaller
    @Operation(summary = "独立踢人（清会话）",
        description = "admin-console 签名调用方清退该用户所有 SSO 会话（独立踢人），<b>不改账号状态</b>"
            + "+ 同步落审计 op_type=REVOKE_SESSIONS。用于单独清退在线会话（如怀疑会话泄露）而不封号。"
            + "该用户当前无在线会话 → 撤销 0 个、正常返回 204（不报错）。成功 → 204。reason 可选。"
            + "@RequireManagementCaller 白名单 gate：非白名单签名 → 403；未签名 → 401。"
            + "委托 AccountManagementAppService.revokeSessions（复用 SsoSessionRevoker.revokeQuietly，best-effort）。"
            + "无业务错误码（链路不查账号、踢人 best-effort；仅基建错可中断）。")
    @ErrorCodes("INTERNAL_SERVER_ERROR")
    public ResponseEntity<Void> revokeSessions(@PathVariable Long userId,
            @Valid @RequestBody(required = false) ManagementReasonCommand command) {
        managementAppService.revokeSessions(userId, ManagementReasonCommand.reasonOrNull(command));
        return ResponseEntity.noContent().build();
    }

    // ========== #70：用户搜索（分页多条件，@RequireManagementCaller，只读不审计）==========

    @GetMapping
    @RequireManagementCaller
    @Operation(summary = "用户搜索（分页多条件）",
        description = "admin-console 签名调用方按 email/phone/userId/status/locked/注册时间区间 分页搜索账号，"
            + "返回分页管理视图（items/total/page/size，每项同管理详情口径 AccountManagementView）。"
            + "首次启用 BaseRepository 的 JpaSpecificationExecutor + findAll(Pageable)——@Condition 多条件 AND 组合，"
            + "不传即不过滤。只读、不审计。无结果 / 分页越界 → 空页（200，不报错）。"
            + "page 0-based / size（默认 createdAt 降序，size 默认 20）。"
            + "@RequireManagementCaller 白名单 gate：非白名单签名 → 403；未签名 → 401（签名 gate）。"
            + "无业务错误码（空页不报错；仅基建错可中断）。")
    @ErrorCodes("INTERNAL_SERVER_ERROR")
    public ApiResponse<PageResponse<AccountManagementView>> search(
            AccountSearchQuery query,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.ok(managementAppService.search(query, pageable));
    }
}
