package com.aieducenter.aieducenteridentity.account.endpoints.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import com.aieducenter.aieducenteridentity.account.domain.aggregate.Account;
import com.aieducenter.aieducenteridentity.account.domain.aggregate.AccountOperationLog;
import com.aieducenter.aieducenteridentity.account.domain.aggregate.Profile;
import com.aieducenter.aieducenteridentity.account.domain.enums.AccountStatus;
import com.aieducenter.aieducenteridentity.account.domain.enums.OperationType;
import com.aieducenter.aieducenteridentity.account.domain.repository.AccountOperationLogRepository;
import com.aieducenter.aieducenteridentity.account.domain.repository.ProfileRepository;
import com.aieducenter.aieducenteridentity.test.IdentityIntegrationTestBase;
import com.aieducenter.aieducenteridentity.test.TestSignatureHelper;
import com.aieducenter.aieducenteridentity.test.WireMockAppRegistryConfig;
import com.cartisan.test.base.ApiTestAssertions;

/**
 * 后台管理 gate + 管理端点集成测试（{@code GET /api/account/{userId}/management}（#67）/
 * {@code POST /api/account/{userId}/disable}（#68）/ {@code activate}·{@code unlock}·{@code sessions/revoke}（#69）/
 * {@code GET /api/account} 用户搜索（#70，首次启用 Specification/Pageable））。
 *
 * <p>一条缝贯穿 签名 filter → {@code SignatureVerificationInterceptor}（401 认证）→
 * {@code ManagementCallerInterceptor}（403 白名单）→ controller → {@code AccountManagementAppService}
 * → 聚合 → DB（+ Redis 踢人）。照搬 {@code SignedAccountControllerIntegrationTest} 的真签名范式（MockMvc +
 * Testcontainers 真 PG/Redis + WireMock app-registry），复用 {@link IdentityIntegrationTestBase}。</p>
 *
 * <p>读端点三入口（#67）：admin-console 签名（白名单内）→ 200；普通签名调用方（白名单外）→ 403；未签名 → 401。</p>
 *
 * <p>封号端点（#68）额外验：disable → status=DISABLED + 该用户所有 SSO 会话清（Redis 踢人）+ 审计落一行
 * （operator 取 {@code X-User-Id/X-User-Name} 头经 {@code RequestContext} 还原 / target / op_type=DISABLE /
 * reason）；reason 缺失 → 400 不办理（且不落审计、不改状态）。</p>
 *
 * <p>解封 / 解锁 / 独立踢人（#69）：activate → status=ACTIVE + 审计 ACTIVATE；unlock → locked=false + 审计 UNLOCK；
 * sessions/revoke → 该用户 SSO 会话清、不改账号状态 + 审计 REVOKE_SESSIONS；无在线会话 revoke → 204 撤销 0 个不报错；
 * 非 admin-console 签名 → 三个新端点均 403。</p>
 *
 * @since 0.1.0
 */
@Transactional
class ManagementEndpointIntegrationTest extends IdentityIntegrationTestBase {

    private static final String PASSWORD = "Pass1234";

    /** admin-console 管理调用方——appName="admin-console"（白名单内），凭据与 WireMock admin-console stub 同源。 */
    private final TestSignatureHelper adminConsoleSigner = new TestSignatureHelper(
        WireMockAppRegistryConfig.ADMIN_CONSOLE_API_KEY,
        WireMockAppRegistryConfig.ADMIN_CONSOLE_API_SECRET);

    /** 普通签名调用方——appName="签名服务测试调用方"（白名单外），凭据与 WireMock signed-caller stub 同源。 */
    private final TestSignatureHelper signedCallerSigner = new TestSignatureHelper(
        WireMockAppRegistryConfig.SIGNED_CALLER_API_KEY,
        WireMockAppRegistryConfig.SIGNED_CALLER_API_SECRET);

    @Autowired
    private AccountOperationLogRepository operationLogRepository;

    @Autowired
    private ProfileRepository profileRepository;

    /** 带 5 个合法签名头 GET（指定 signer）。 */
    private MockHttpServletRequestBuilder signedGet(String path, TestSignatureHelper signer) {
        MockHttpServletRequestBuilder req = get(path);
        signer.sign(null).forEach(req::header);
        return req;
    }

    /** 带 5 个合法签名头 GET + query 参数（指定 signer）——query 不计入签名（framework queryParams 恒空）。 */
    private MockHttpServletRequestBuilder signedGetParams(String path, Map<String, String> params,
            TestSignatureHelper signer) {
        MockHttpServletRequestBuilder req = signedGet(path, signer);
        params.forEach(req::param);
        return req;
    }

    /** 带 5 个合法签名头 POST JSON（指定 signer）；operator 身份经 X-User-Id/X-User-Name 头透传。 */
    private MockHttpServletRequestBuilder signedPost(String path, String body, TestSignatureHelper signer,
            Long operatorUserId, String operatorName) {
        MockHttpServletRequestBuilder req = post(path)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body)
            .header("X-User-Id", String.valueOf(operatorUserId))
            .header("X-User-Name", operatorName);
        signer.sign(body).forEach(req::header);
        return req;
    }

    @Test
    void given_admin_console_signature_when_get_management_detail_then_returns_status_locked_hasPassword_and_profile()
            throws Exception {
        String email = "mgmt-detail@example.com";
        Long userId = createEmailAccount(email, PASSWORD);

        mvc.perform(signedGet("/api/account/" + userId + "/management", adminConsoleSigner))
            .andExpect(ApiTestAssertions.assertOk())
            .andExpect(jsonPath("$.data.userId").value(userId))
            .andExpect(jsonPath("$.data.email").value(email))
            .andExpect(jsonPath("$.data.status").value(1)) // AccountStatus.ACTIVE（BaseEnum → Integer code）
            .andExpect(jsonPath("$.data.locked").value(false))
            .andExpect(jsonPath("$.data.hasPassword").value(true));
    }

    @Test
    void given_non_admin_console_signature_when_get_management_detail_then_returns_403() throws Exception {
        Long userId = createEmailAccount("mgmt-403@example.com", PASSWORD);

        // 签名有效（过 401 认证 gate），但调用方非白名单 → 403
        mvc.perform(signedGet("/api/account/" + userId + "/management", signedCallerSigner))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void given_no_signature_when_get_management_detail_then_returns_401() throws Exception {
        Long userId = createEmailAccount("mgmt-401@example.com", PASSWORD);

        // 无签名头 → 签名 gate 回 401（非白名单 403 之前）
        mvc.perform(get("/api/account/" + userId + "/management"))
            .andExpect(status().isUnauthorized());
    }

    // ========== #68：POST /{userId}/disable（封号，含自动踢人 + 审计）==========

    /**
     * 辅助：取本测试（@Transactional 回滚隔离）写入的审计行——按 target 过滤，跨测试无串扰。
     */
    private AccountOperationLog auditFor(Long targetUserId) {
        return operationLogRepository.findAll().stream()
            .filter(log -> log.getTargetUserId().equals(targetUserId))
            .reduce((a, b) -> b) // 万一多行取最后一条
            .orElseThrow();
    }

    @Test
    void given_admin_console_signature_when_disable_then_status_disabled_and_sessions_cleared_and_audit_logged()
            throws Exception {
        String email = "disable-sig@example.com";
        Long userId = createEmailAccount(email, PASSWORD);
        Long operatorId = 9001L;
        String operatorName = "alice";
        String reason = "违规内容，多次警告";

        // 预置一个 SSO 会话（disable 应自动踢人——清该 userId 所有 SSO 会话）
        String sessionId = createSsoSession(userId, "用户").sessionId();
        assertThat(sessionRepository.findActive(sessionId)).isPresent();

        String body = "{\"reason\":\"" + reason + "\"}";

        mvc.perform(signedPost("/api/account/" + userId + "/disable", body, adminConsoleSigner, operatorId,
                operatorName))
            .andExpect(status().isNoContent());

        // AC①：status=DISABLED
        Account account = accountRepository.findById(userId).orElseThrow();
        assertThat(account.getStatus()).isEqualTo(AccountStatus.DISABLED);

        // AC②：该用户所有 SSO 会话被清（Redis 踢人）
        assertThat(sessionRepository.findActive(sessionId)).isEmpty();

        // AC③：account_operation_log 落一行——operator（RequestContext）/ target / op_type=DISABLE / reason
        AccountOperationLog log = auditFor(userId);
        assertThat(log.getOpType()).isEqualTo(OperationType.DISABLE);
        assertThat(log.getTargetUserId()).isEqualTo(userId);
        assertThat(log.getReason()).isEqualTo(reason);
        assertThat(log.getOperatorCaller()).isEqualTo(WireMockAppRegistryConfig.ADMIN_CONSOLE_APP_NAME);
        assertThat(log.getOperatorUserId()).isEqualTo(operatorId);
        assertThat(log.getOperatorUserName()).isEqualTo(operatorName);
        assertThat(log.getOccurredAt()).isNotNull();
    }

    @Test
    void given_non_admin_console_signature_when_disable_then_returns_403_and_no_side_effects() throws Exception {
        Long userId = createEmailAccount("disable-403@example.com", PASSWORD);

        // 签名有效（过 401 认证 gate），但调用方非白名单 → 403
        mvc.perform(signedPost("/api/account/" + userId + "/disable", "{\"reason\":\"x\"}", signedCallerSigner,
                9002L, "bob"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value(403));

        // 未办理：状态未变、无审计
        assertThat(accountRepository.findById(userId).orElseThrow().getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(operationLogRepository.findAll()).isEmpty();
    }

    @Test
    void given_no_signature_when_disable_then_returns_401() throws Exception {
        Long userId = createEmailAccount("disable-401@example.com", PASSWORD);

        // 无签名头 → 签名 gate 回 401（白名单 403 之前）
        mvc.perform(post("/api/account/" + userId + "/disable")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"x\"}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void given_admin_console_signature_but_reason_missing_when_disable_then_returns_400_and_no_side_effects()
            throws Exception {
        Long userId = createEmailAccount("disable-noreason@example.com", PASSWORD);

        // reason 缺失 → 400（@NotBlank），不办理
        mvc.perform(signedPost("/api/account/" + userId + "/disable", "{}", adminConsoleSigner, 9003L, "carol"))
            .andExpect(status().isBadRequest())
            .andExpect(ApiTestAssertions.assertError(400));

        // 未办理：状态未变、无审计
        assertThat(accountRepository.findById(userId).orElseThrow().getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(operationLogRepository.findAll()).isEmpty();
    }

    @Test
    void given_admin_console_signature_but_unknown_user_when_disable_then_returns_404() throws Exception {
        // 不存在的 userId → 404 USER_NOT_FOUND（标准 ApiResponse，非 OIDC {error}）
        mvc.perform(signedPost("/api/account/99999999999/disable", "{\"reason\":\"x\"}", adminConsoleSigner, 9004L,
                "dave"))
            .andExpect(status().isNotFound())
            .andExpect(ApiTestAssertions.assertError(404))
            .andExpect(jsonPath("$.error").doesNotExist());
    }

    // ========== #69：POST /{userId}/activate、/{userId}/unlock、/{userId}/sessions/revoke ==========

    @Test
    void given_admin_console_signature_when_activate_then_status_active_and_audit_logged() throws Exception {
        Long userId = createEmailAccount("activate@example.com", PASSWORD);
        Long operatorId = 9101L;
        String operatorName = "activate-op";
        String reason = "申诉成功，恢复账号";

        // 预置封号状态（经聚合直接置 DISABLED，不经封号端点——隔离审计，只验 activate 这一行）
        Account disabled = accountRepository.findById(userId).orElseThrow();
        disabled.disable();
        accountRepository.save(disabled);
        assertThat(disabled.getStatus()).isEqualTo(AccountStatus.DISABLED);

        String body = "{\"reason\":\"" + reason + "\"}";

        mvc.perform(signedPost("/api/account/" + userId + "/activate", body, adminConsoleSigner, operatorId,
                operatorName))
            .andExpect(status().isNoContent());

        // AC①：status=ACTIVE
        assertThat(accountRepository.findById(userId).orElseThrow().getStatus()).isEqualTo(AccountStatus.ACTIVE);

        // 审计 ACTIVATE（operator/target/reason）
        AccountOperationLog log = auditFor(userId);
        assertThat(log.getOpType()).isEqualTo(OperationType.ACTIVATE);
        assertThat(log.getTargetUserId()).isEqualTo(userId);
        assertThat(log.getReason()).isEqualTo(reason);
        assertThat(log.getOperatorCaller()).isEqualTo(WireMockAppRegistryConfig.ADMIN_CONSOLE_APP_NAME);
        assertThat(log.getOperatorUserId()).isEqualTo(operatorId);
        assertThat(log.getOperatorUserName()).isEqualTo(operatorName);
    }

    @Test
    void given_admin_console_signature_when_unlock_then_locked_false_and_audit_logged() throws Exception {
        Long userId = createEmailAccount("unlock@example.com", PASSWORD);
        Long operatorId = 9102L;
        String operatorName = "unlock-op";
        String reason = "风控误判，解除锁定";

        // 预置锁定状态
        Account locked = accountRepository.findById(userId).orElseThrow();
        locked.lock();
        accountRepository.save(locked);
        assertThat(locked.isLocked()).isTrue();

        String body = "{\"reason\":\"" + reason + "\"}";

        mvc.perform(signedPost("/api/account/" + userId + "/unlock", body, adminConsoleSigner, operatorId,
                operatorName))
            .andExpect(status().isNoContent());

        // AC②：locked=false
        assertThat(accountRepository.findById(userId).orElseThrow().isLocked()).isFalse();

        // 审计 UNLOCK
        AccountOperationLog log = auditFor(userId);
        assertThat(log.getOpType()).isEqualTo(OperationType.UNLOCK);
        assertThat(log.getTargetUserId()).isEqualTo(userId);
        assertThat(log.getReason()).isEqualTo(reason);
        assertThat(log.getOperatorUserName()).isEqualTo(operatorName);
    }

    @Test
    void given_admin_console_signature_when_revoke_sessions_then_sessions_cleared_status_unchanged_and_audit_logged()
            throws Exception {
        Long userId = createEmailAccount("revoke@example.com", PASSWORD);
        Long operatorId = 9103L;
        String operatorName = "revoke-op";
        String reason = "安全处置，清退在线会话";

        // 预置一个 SSO 会话（revoke 应清空）
        String sessionId = createSsoSession(userId, "用户").sessionId();
        assertThat(sessionRepository.findActive(sessionId)).isPresent();

        // 记录初始状态——revoke 不应改账号状态
        AccountStatus statusBefore = accountRepository.findById(userId).orElseThrow().getStatus();

        String body = "{\"reason\":\"" + reason + "\"}";

        mvc.perform(signedPost("/api/account/" + userId + "/sessions/revoke", body, adminConsoleSigner, operatorId,
                operatorName))
            .andExpect(status().isNoContent());

        // AC③：该用户 SSO 会话清空
        assertThat(sessionRepository.findActive(sessionId)).isEmpty();

        // AC③：不改账号状态
        assertThat(accountRepository.findById(userId).orElseThrow().getStatus()).isEqualTo(statusBefore);

        // 审计 REVOKE_SESSIONS
        AccountOperationLog log = auditFor(userId);
        assertThat(log.getOpType()).isEqualTo(OperationType.REVOKE_SESSIONS);
        assertThat(log.getTargetUserId()).isEqualTo(userId);
        assertThat(log.getReason()).isEqualTo(reason);
        assertThat(log.getOperatorUserName()).isEqualTo(operatorName);
    }

    @Test
    void given_admin_console_signature_when_revoke_sessions_and_none_exist_then_204_and_audit_logged()
            throws Exception {
        Long userId = createEmailAccount("revoke-none@example.com", PASSWORD);
        // 该用户当前无在线会话

        mvc.perform(signedPost("/api/account/" + userId + "/sessions/revoke", "{}", adminConsoleSigner, 9104L,
                "revoke-none-op"))
            .andExpect(status().isNoContent());

        // AC④：正常撤销 0 个、不报错（已由 204 表明）；审计仍落一行 REVOKE_SESSIONS
        AccountOperationLog log = auditFor(userId);
        assertThat(log.getOpType()).isEqualTo(OperationType.REVOKE_SESSIONS);
        assertThat(log.getTargetUserId()).isEqualTo(userId);
    }

    @Test
    void given_non_admin_console_signature_when_activate_unlock_or_revoke_then_returns_403_and_no_side_effects()
            throws Exception {
        Long userId = createEmailAccount("mgmt-403-batch@example.com", PASSWORD);

        // 签名有效（过 401 认证 gate），但调用方非白名单 → 三个新端点均 403
        mvc.perform(signedPost("/api/account/" + userId + "/activate", "{}", signedCallerSigner, 9105L, "x"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value(403));
        mvc.perform(signedPost("/api/account/" + userId + "/unlock", "{}", signedCallerSigner, 9105L, "x"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value(403));
        mvc.perform(signedPost("/api/account/" + userId + "/sessions/revoke", "{}", signedCallerSigner, 9105L, "x"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value(403));

        // 未办理：无审计
        assertThat(operationLogRepository.findAll()).isEmpty();
    }

    // ========== #70：GET /api/account（用户搜索，分页多条件，首次启用 Specification/Pageable）==========

    @Test
    void given_email_filter_when_search_then_returns_only_matching_accounts_with_paged_shape() throws Exception {
        createEmailAccount("alpha-search@example.com", PASSWORD);
        createEmailAccount("beta-search@example.com", PASSWORD);
        createEmailAccount("gamma-other@example.com", PASSWORD);

        // email INNER_LIKE：3 个里命中含 "search" 的 2 个；page/size/total/items 形状齐
        mvc.perform(signedGetParams("/api/account", Map.of("email", "search"), adminConsoleSigner))
            .andExpect(ApiTestAssertions.assertOk())
            .andExpect(jsonPath("$.data.total").value(2))
            .andExpect(jsonPath("$.data.items.length()").value(2))
            .andExpect(jsonPath("$.data.page").value(1)) // 0-based page=0 → 1-based
            .andExpect(jsonPath("$.data.size").value(20)) // @PageableDefault 默认 size
            .andExpect(jsonPath("$.data.items[*].email",
                containsInAnyOrder("alpha-search@example.com", "beta-search@example.com")));

        // 读操作不审计：搜索不留任何操作流水
        assertThat(operationLogRepository.findAll()).isEmpty();
    }

    @Test
    void given_status_filter_when_search_then_returns_only_disabled() throws Exception {
        Long active = createEmailAccount("status-active@example.com", PASSWORD);
        Long disabled = createEmailAccount("status-disabled@example.com", PASSWORD);
        Account toDisable = accountRepository.findById(disabled).orElseThrow();
        toDisable.disable();
        accountRepository.save(toDisable);
        assertThat(accountRepository.findById(active).orElseThrow().getStatus()).isEqualTo(AccountStatus.ACTIVE);

        // status=0（DISABLED，BaseEnum 按 code 绑定）→ 只回被封的那个
        mvc.perform(signedGetParams("/api/account", Map.of("status", "0"), adminConsoleSigner))
            .andExpect(ApiTestAssertions.assertOk())
            .andExpect(jsonPath("$.data.total").value(1))
            .andExpect(jsonPath("$.data.items[0].userId").value(disabled))
            .andExpect(jsonPath("$.data.items[0].status").value(0));
    }

    @Test
    void given_locked_filter_when_search_then_returns_only_locked() throws Exception {
        Long unlocked = createEmailAccount("locked-false@example.com", PASSWORD);
        Long locked = createEmailAccount("locked-true@example.com", PASSWORD);
        Account toLock = accountRepository.findById(locked).orElseThrow();
        toLock.lock();
        accountRepository.save(toLock);
        assertThat(accountRepository.findById(unlocked).orElseThrow().isLocked()).isFalse();

        // locked=true → 只回被锁的那个（不传 locked 即不过滤）
        mvc.perform(signedGetParams("/api/account", Map.of("locked", "true"), adminConsoleSigner))
            .andExpect(ApiTestAssertions.assertOk())
            .andExpect(jsonPath("$.data.total").value(1))
            .andExpect(jsonPath("$.data.items[0].userId").value(locked))
            .andExpect(jsonPath("$.data.items[0].locked").value(true));
    }

    @Test
    void given_userId_filter_when_search_then_returns_that_user_with_profile_nickname() throws Exception {
        Long target = createEmailAccount("by-userid@example.com", PASSWORD);
        createEmailAccount("by-userid-other@example.com", PASSWORD);
        // 给 target 建一份 Profile——验证列表项批量补取 profile（nickname 入项）
        profileRepository.save(Profile.create(target, "target-nick", null));

        // userId（EQUAL，propName=id）→ 只回 target；且带出 profile 的 nickname
        mvc.perform(signedGetParams("/api/account", Map.of("userId", String.valueOf(target)), adminConsoleSigner))
            .andExpect(ApiTestAssertions.assertOk())
            .andExpect(jsonPath("$.data.total").value(1))
            .andExpect(jsonPath("$.data.items[0].userId").value(target))
            .andExpect(jsonPath("$.data.items[0].nickname").value("target-nick"));
    }

    @Test
    void given_created_range_when_search_then_broad_includes_and_past_excludes() throws Exception {
        createEmailAccount("range@example.com", PASSWORD);

        // 宽区间（2000 ~ 2099）含「现在」建号的账号 → 命中 1
        mvc.perform(signedGetParams("/api/account",
                Map.of("createdFrom", "2000-01-01T00:00:00", "createdTo", "2099-12-31T23:59:59"),
                adminConsoleSigner))
            .andExpect(ApiTestAssertions.assertOk())
            .andExpect(jsonPath("$.data.total").value(1))
            .andExpect(jsonPath("$.data.items[0].email").value("range@example.com"));

        // 过去上界（createdTo=2000）→ 账号都建在「现在」之后 → 0
        mvc.perform(signedGetParams("/api/account",
                Map.of("createdTo", "2000-01-01T00:00:00"), adminConsoleSigner))
            .andExpect(ApiTestAssertions.assertOk())
            .andExpect(jsonPath("$.data.total").value(0))
            .andExpect(jsonPath("$.data.items.length()").value(0));
    }

    @Test
    void given_combined_email_and_status_when_search_then_intersects() throws Exception {
        createEmailAccount("combo-active@example.com", PASSWORD); // email 命中 + ACTIVE
        Long disabled = createEmailAccount("combo-disabled@example.com", PASSWORD); // email 命中 + DISABLED
        Account toDisable = accountRepository.findById(disabled).orElseThrow();
        toDisable.disable();
        accountRepository.save(toDisable);

        // email=combo AND status=0（DISABLED）→ 交集只回 combo-disabled
        mvc.perform(signedGetParams("/api/account",
                Map.of("email", "combo", "status", "0"), adminConsoleSigner))
            .andExpect(ApiTestAssertions.assertOk())
            .andExpect(jsonPath("$.data.total").value(1))
            .andExpect(jsonPath("$.data.items[0].email").value("combo-disabled@example.com"))
            .andExpect(jsonPath("$.data.items[0].status").value(0));
    }

    @Test
    void given_pagination_when_search_then_returns_correct_slices_and_total() throws Exception {
        createEmailAccount("page-1@example.com", PASSWORD);
        createEmailAccount("page-2@example.com", PASSWORD);
        createEmailAccount("page-3@example.com", PASSWORD);

        // 共 3 个，size=2：第一页 2 项 / total=3 / page=1（1-based）
        mvc.perform(signedGetParams("/api/account",
                Map.of("email", "page", "page", "0", "size", "2"), adminConsoleSigner))
            .andExpect(ApiTestAssertions.assertOk())
            .andExpect(jsonPath("$.data.total").value(3))
            .andExpect(jsonPath("$.data.items.length()").value(2))
            .andExpect(jsonPath("$.data.page").value(1))
            .andExpect(jsonPath("$.data.size").value(2));

        // 第二页 1 项 / total=3 / page=2
        mvc.perform(signedGetParams("/api/account",
                Map.of("email", "page", "page", "1", "size", "2"), adminConsoleSigner))
            .andExpect(ApiTestAssertions.assertOk())
            .andExpect(jsonPath("$.data.total").value(3))
            .andExpect(jsonPath("$.data.items.length()").value(1))
            .andExpect(jsonPath("$.data.page").value(2));
    }

    @Test
    void given_no_match_or_page_out_of_bounds_when_search_then_empty_page_200() throws Exception {
        createEmailAccount("empty@example.com", PASSWORD);

        // 无命中 → 空页（items=[]、total=0），不报错（200）
        mvc.perform(signedGetParams("/api/account", Map.of("email", "zzz-no-such"), adminConsoleSigner))
            .andExpect(ApiTestAssertions.assertOk())
            .andExpect(jsonPath("$.data.total").value(0))
            .andExpect(jsonPath("$.data.items.length()").value(0));

        // 分页越界（1 个账号却取第 100 页）→ 空页（items=[]、total 仍 1），不报错（200）
        mvc.perform(signedGetParams("/api/account",
                Map.of("page", "99", "size", "2"), adminConsoleSigner))
            .andExpect(ApiTestAssertions.assertOk())
            .andExpect(jsonPath("$.data.total").value(1))
            .andExpect(jsonPath("$.data.items.length()").value(0));
    }

    @Test
    void given_non_admin_console_signature_or_no_signature_when_search_then_403_then_401() throws Exception {
        createEmailAccount("gate@example.com", PASSWORD);

        // 签名有效（过 401 认证 gate），但调用方非白名单 → 403
        mvc.perform(signedGetParams("/api/account", Map.of("email", "gate"), signedCallerSigner))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value(403));

        // 无签名头 → 签名 gate 回 401（白名单 403 之前）
        mvc.perform(get("/api/account").param("email", "gate"))
            .andExpect(status().isUnauthorized());
    }
}
