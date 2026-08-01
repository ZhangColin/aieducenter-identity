# SSO Demo Consumer + .localhost 本地联调 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让一个标准 cartisan-boot 前后端分离项目（demo）真跑通 identity 的 SSO 闭环（首次登录 + 二次免登），并提供 `.localhost` 本地联调基建与 dev 一键登。

**Architecture:** identity 仓内 monorepo `demo/`（`demo-backend` 当 BFF/OIDC 消费方，`demo-web` 显示页）；identity 后端加一个配置开关 `identity.sso.dev-login.enabled` 门控的 dev 一键登端点 + 测试账号 seeder，在 identity-web 登录页缺席时兜住「登录页」位置。token 只存 BFF 服务端（内存会话），浏览器只持业务 cookie。

**Tech Stack:** Java 21 + `--enable-preview`、Spring Boot 3.4.1、parent `com.cartisan:cartisan-boot:0.1.0-SNAPSHOT`、Nimbus JOSE+JWT（解 id_token）；demo-web = Next.js 15 / React 19 / Tailwind（cartisan-boot 前端脚手架）；本地 PG16 + Redis7（docker-compose）。

**Spec:** `docs/superpowers/specs/2026-08-01-sso-demo-consumer-and-local-dev-design.md`

## Global Constraints

- Java 21，编译/运行均带 `--enable-preview`（parent 已配 surefire argLine）。
- 所有 identity 侧新代码落在现有 `com.aieducenter.aieducenteridentity.sso` 包结构内，遵循六边形分层（controller→application→domain），`ArchitectureTest` 守卫。
- dev 一键登 + seeder **只用配置开关** `identity.sso.dev-login.enabled=true` 启用（不用 `@Profile`），prod 永不设此开关 → bean 不注册。
- token 只存 BFF 内存（`ConcurrentHashMap`），浏览器不接触 token；业务 cookie `demo_session`（httpOnly+Secure+SameSite-Lax）。
- 本期**不调** `/userinfo`、**不验** id_token 签名（#17 未建）；用户信息从 id_token claims 解。
- 仓库布局：`demo/demo-backend/`、`demo/demo-web/` 是独立子项目，identity **根 pom 不改、不收**它们。
- 提交直接落 `develop` 分支（不另开 feature 分支）。
- identity 测试 seam = MockMvc 黑盒（`@ActiveProfiles("test")` + Testcontainers）；demo-backend 控制器测用 `MockMvcBuilders.standaloneSetup`；OIDC 客户端测用 `MockRestServiceServer`。

## File Structure

**identity 仓（修改/新增）：**
- Modify: `src/main/java/.../sso/config/SsoProperties.java` — 加 `DevLogin` 内嵌配置（开关 + 测试账号 email/password/nickname）。
- Create: `src/main/java/.../sso/application/DevLoginAppService.java` — 镜像 `SsoLoginAppService.loginByPassword`，免认证、登预置账号。
- Create: `src/main/java/.../sso/endpoints/controller/DevLoginController.java` — `GET /api/auth/dev-login`（配置开关门控）。
- Create: `src/main/java/.../sso/dev/DevAccountSeeder.java` — 启动种测试账号（配置开关门控）。
- Create: `src/test/java/.../sso/endpoints/DevLoginFlowIntegrationTest.java` — dev-login 闭环 MockMvc 测试。
- Modify: `src/main/resources/application-local.yml` — 开 dev-login + 覆盖 login-page-url/redirect 白名单。
- Create: `docker-compose.yml` — 本地 PG + Redis。
- Create: `dev-up.sh` — 一键起本地基建 + 打印三个进程命令。
- Create: `docs/guide/local-sso-debugging.md` — 联调手册。
- Modify: `CONTEXT.md` — 「开发测试」段落补 .localhost 端口与 dev-login 兜底说明。

**demo-backend（新增，独立项目 `demo/demo-backend/`）：**
- `pom.xml`、`src/main/java/com/aieducenter/demobff/DemoBffApplication.java`
- `config/SsoProperties.java`（issuer/clientId/secret/redirectUri/scope/appBaseUrl）
- `sso/BffSession.java`、`sso/BffSessionStore.java`、`sso/OidcClient.java`、`sso/TokenResponse.java`、`sso/AuthController.java`、`sso/MeController.java`
- `src/main/resources/application.yml` + `application-local.yml`
- 测试：`sso/OidcClientTest.java`、`sso/AuthControllerTest.java`、`sso/MeControllerTest.java`

**demo-web（新增，独立项目 `demo/demo-web/`）：**
- `package.json`、`next.config.mjs`（rewrite `/api/*`、`/auth/*`→backend）、`src/app/layout.tsx`、`src/app/page.tsx`、`src/lib/api.ts`
- 其余脚手架默认文件（tailwind/postcss/globals.css/.eslintrc）取自 cartisan-boot 前端脚手架。

---

## Task 1: SsoProperties 加 dev-login 配置

**Files:**
- Modify: `src/main/java/com/aieducenter/aieducenteridentity/sso/config/SsoProperties.java`

**Interfaces:**
- Produces: `SsoProperties.getDevLogin()` → `DevLogin{ enabled, accountEmail, accountPassword, accountNickname }`，绑定 `identity.sso.dev-login.*`。`@ConditionalOnProperty(prefix="identity.sso.dev-login", name="enabled", havingValue="true")` 后续任务据此门控。

- [ ] **Step 1: 写失败测试**

Create `src/test/java/com/aieducenter/aieducenteridentity/sso/config/SsoPropertiesTest.java`:

```java
package com.aieducenter.aieducenteridentity.sso.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.util.Map;

class SsoPropertiesTest {

    private SsoProperties bind(Map<String, String> source) {
        return new Binder(new MapConfigurationPropertySource(source))
            .bind("identity.sso", SsoProperties.class).get();
    }

    @Test
    void devLogin_disabled_by_default() {
        assertThat(new SsoProperties().getDevLogin().isEnabled()).isFalse();
    }

    @Test
    void devLogin_binds_from_relaxed_names() {
        SsoProperties p = bind(Map.of(
            "identity.sso.dev-login.enabled", "true",
            "identity.sso.dev-login.account-email", "demo@aieducenter.com",
            "identity.sso.dev-login.account-password", "demo12345",
            "identity.sso.dev-login.account-nickname", "Demo 用户"));
        assertThat(p.getDevLogin().isEnabled()).isTrue();
        assertThat(p.getDevLogin().getAccountEmail()).isEqualTo("demo@aieducenter.com");
        assertThat(p.getDevLogin().getAccountNickname()).isEqualTo("Demo 用户");
    }
}
```

- [ ] **Step 2: 跑测试验证失败**

Run: `mvn test -Dtest=SsoPropertiesTest -q`
Expected: FAIL（`getDevLogin()` 不存在，编译失败）。

- [ ] **Step 3: 最小实现**

在 `SsoProperties.java` 末尾（最后一个 setter 之后、类闭合 `}` 之前）加字段 + 内嵌类 + getter/setter：

```java
    // ── dev 一键登（#16：identity-web 缺席时兜登录页；仅 dev/local，prod 不开此开关） ──

    /** dev 一键登配置（开关 + 预置测试账号）。 */
    private DevLogin devLogin = new DevLogin();

    public DevLogin getDevLogin() {
        return devLogin;
    }

    public void setDevLogin(DevLogin devLogin) {
        this.devLogin = devLogin;
    }

    /** dev 一键登配置项。开关默认 false；开启后 {@code /authorize} 无 cookie 跳本端点、自动登预置账号。 */
    public static class DevLogin {
        /** 是否启用 dev 一键登（prod 必须为 false）。 */
        private boolean enabled = false;
        /** 预置测试账号邮箱（dev-login 按此定位账号）。 */
        private String accountEmail = "demo@aieducenter.com";
        /** 预置测试账号明文密码（seeder 加密入库；也供真实登录页联调用）。 */
        private String accountPassword = "demo12345";
        /** 预置测试账号昵称。 */
        private String accountNickname = "Demo 用户";

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getAccountEmail() { return accountEmail; }
        public void setAccountEmail(String accountEmail) { this.accountEmail = accountEmail; }
        public String getAccountPassword() { return accountPassword; }
        public void setAccountPassword(String accountPassword) { this.accountPassword = accountPassword; }
        public String getAccountNickname() { return accountNickname; }
        public void setAccountNickname(String accountNickname) { this.accountNickname = accountNickname; }
    }
```

- [ ] **Step 4: 跑测试验证通过**

Run: `mvn test -Dtest=SsoPropertiesTest -q`
Expected: PASS（2 tests）。

- [ ] **Step 5: 提交**

```bash
git add src/main/java/com/aieducenter/aieducenteridentity/sso/config/SsoProperties.java \
        src/test/java/com/aieducenter/aieducenteridentity/sso/config/SsoPropertiesTest.java
git commit -m "feat(#16): SsoProperties 加 dev-login 配置开关与预置账号"
```

---

## Task 2: DevAccountSeeder（启动种测试账号）

**Files:**
- Create: `src/main/java/com/aieducenter/aieducenteridentity/sso/dev/DevAccountSeeder.java`
- Create: `src/main/java/com/aieducenter/aieducenteridentity/sso/dev/package-info.java`

**Interfaces:**
- Consumes: `SsoProperties.getDevLogin()`、`AccountRepository`、`ProfileRepository`、`AccountPasswordEncoderService`、`Account.register(email,phone,encoded)`、`Profile.create(userId,nickname,avatar)`。
- Produces: 当 `identity.sso.dev-login.enabled=true` 时，启动后 DB 里有 `demo@aieducenter.com` 账号 + Profile。

- [ ] **Step 1: 写失败测试**

Create `src/test/java/com/aieducenter/aieducenteridentity/sso/dev/DevAccountSeederTest.java`:

```java
package com.aieducenter.aieducenteridentity.sso.dev;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

import com.aieducenter.aieducenteridentity.account.domain.repository.AccountRepository;
import com.aieducenter.aieducenteridentity.test.IdentityIntegrationTestBase;

/**
 * seeder 在上下文启动时跑（dev-login 开关开）；这里复用已起好的上下文验证账号已种。
 * 开关由 @TestPropertySource 打开 → 上下文启动时 seeder 注册并执行。
 */
@TestPropertySource(properties = "identity.sso.dev-login.enabled=true")
@DirtiesContext  // 本类用带 dev-login 的上下文，与默认上下文隔离
class DevAccountSeederTest extends IdentityIntegrationTestBase {

    @Autowired
    private AccountRepository accountRepository;

    @Test
    void seeded_demo_account_exists() {
        assertThat(accountRepository.findByEmail("demo@aieducenter.com"))
            .as("seeder 应在启动时种入 demo@aieducenter.com")
            .isPresent();
    }
}
```

- [ ] **Step 2: 跑测试验证失败**

Run: `mvn test -Dtest=DevAccountSeederTest -q`
Expected: FAIL（账号不存在 / seeder 未注册）。

- [ ] **Step 3: 最小实现**

Create `src/main/java/com/aieducenter/aieducenteridentity/sso/dev/package-info.java`:

```java
/**
 * dev 一键登支持：启动种测试账号（identity-web 登录页缺席时的联调便利，issue #16）。
 * 全部由 {@code identity.sso.dev-login.enabled} 门控，prod 不开。
 */
package com.aieducenter.aieducenteridentity.sso.dev;
```

Create `src/main/java/com/aieducenter/aieducenteridentity/sso/dev/DevAccountSeeder.java`:

```java
package com.aieducenter.aieducenteridentity.sso.dev;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.aieducenter.aieducenteridentity.account.domain.aggregate.Account;
import com.aieducenter.aieducenteridentity.account.domain.aggregate.Profile;
import com.aieducenter.aieducenteridentity.account.domain.repository.AccountRepository;
import com.aieducenter.aieducenteridentity.account.domain.repository.ProfileRepository;
import com.aieducenter.aieducenteridentity.account.domain.service.AccountPasswordEncoderService;
import com.aieducenter.aieducenteridentity.sso.config.SsoProperties;

/**
 * dev 测试账号 seeder（issue #16）。
 *
 * <p>{@code identity.sso.dev-login.enabled=true} 时启动：若 {@code demo@aieducenter.com} 不存在则建账号 + Profile。
 * 幂等（已存在即跳过）。dev-login 端点登这个账号。仅 dev/local 联调用，prod 不开此开关。</p>
 */
@Component
@ConditionalOnProperty(prefix = "identity.sso.dev-login", name = "enabled", havingValue = "true")
public class DevAccountSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevAccountSeeder.class);

    private final AccountRepository accountRepository;
    private final ProfileRepository profileRepository;
    private final AccountPasswordEncoderService passwordEncoderService;
    private final SsoProperties properties;

    public DevAccountSeeder(AccountRepository accountRepository, ProfileRepository profileRepository,
            AccountPasswordEncoderService passwordEncoderService, SsoProperties properties) {
        this.accountRepository = accountRepository;
        this.profileRepository = profileRepository;
        this.passwordEncoderService = passwordEncoderService;
        this.properties = properties;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        SsoProperties.DevLogin cfg = properties.getDevLogin();
        if (accountRepository.existsByEmail(cfg.getAccountEmail())) {
            return;
        }
        String encoded = passwordEncoderService.encodePassword(cfg.getAccountPassword());
        Account saved = accountRepository.save(
            Account.register(cfg.getAccountEmail(), null, encoded));
        profileRepository.save(Profile.create(saved.getId(), cfg.getAccountNickname(), null));
        log.info("dev 测试账号已种：{}（userId={}）", cfg.getAccountEmail(), saved.getId());
    }
}
```

- [ ] **Step 4: 跑测试验证通过**

Run: `mvn test -Dtest=DevAccountSeederTest -q`
Expected: PASS。

- [ ] **Step 5: 提交**

```bash
git add src/main/java/com/aieducenter/aieducenteridentity/sso/dev/ \
        src/test/java/com/aieducenter/aieducenteridentity/sso/dev/DevAccountSeederTest.java
git commit -m "feat(#16): DevAccountSeeder——dev 启动种测试账号（开关门控）"
```

---

## Task 3: DevLoginAppService + DevLoginController（dev 一键登端点）

**Files:**
- Create: `src/main/java/com/aieducenter/aieducenteridentity/sso/application/DevLoginAppService.java`
- Create: `src/main/java/com/aieducenter/aieducenteridentity/sso/endpoints/controller/DevLoginController.java`
- Create: `src/test/java/com/aieducenter/aieducenteridentity/sso/endpoints/DevLoginFlowIntegrationTest.java`

**Interfaces:**
- Consumes: `SsoClientValidationService.requireActiveClient/requireRedirectUri`、`AccountRepository.findByEmail`、`Account.ensureLoginable/displayLabel`、`ProfileRepository.findById`、`SsoSessionRepository.create(userId,displayName)`、`AuthorizationCodeAppService.issueCodeAndRedirect(userId,client,redirectUri,nonce,scope,state)`、`SsoCookieService.setSessionCookie`。
- Produces: `GET /api/auth/dev-login?client_id&redirect_uri&state&nonce` → 302 `redirect_uri?code&state` + Set-Cookie `sso_session`。镜像 `/api/auth/login`，仅免认证、登预置账号。

- [ ] **Step 1: 写失败测试**

Create `src/test/java/com/aieducenter/aieducenteridentity/sso/endpoints/DevLoginFlowIntegrationTest.java`:

```java
package com.aieducenter.aieducenteridentity.sso.endpoints;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import com.aieducenter.aieducenteridentity.account.domain.repository.AccountRepository;

/**
 * dev 一键登闭环（issue #16）：identity-web 缺席时 /authorize 无 cookie 跳 dev-login，
 * dev-login 自动登预置测试账号 → 发 code → /token 换 token；二次凭 cookie 免登。
 * 打开 dev-login 开关后 seeder 已在上下文启动时种入 demo@aieducenter.com。
 */
@Transactional
@DirtiesContext
@TestPropertySource(properties = {
    "identity.sso.dev-login.enabled=true",
    "identity.sso.login-page-url=http://localhost/api/auth/dev-login",
    "identity.sso.stub-redirect-uris[0]=http://demo.localhost:3000/auth/callback"
})
class DevLoginFlowIntegrationTest extends SsoIntegrationTestBase {

    private static final String REDIRECT = "http://demo.localhost:3000/auth/callback";

    @Autowired
    private AccountRepository accountRepository;

    @Test
    void authorize_without_cookie_dev_login_then_token() throws Exception {
        assertThat(accountRepository.findByEmail("demo@aieducenter.com")).isPresent();

        // 1. /authorize 无 cookie → 302 dev-login（透传 client_id/redirect_uri/state/nonce）
        MvcResult auth = mvc.perform(get("/authorize")
                .param("client_id", CLIENT_ID)
                .param("redirect_uri", REDIRECT)
                .param("state", "s1")
                .param("nonce", "n1"))
            .andExpect(status().isFound())
            .andExpect(header().string("Location",
                org.hamcrest.Matchers.startsWith("http://localhost/api/auth/dev-login")))
            .andReturn();
        String devLoginUrl = auth.getResponse().getHeader("Location");

        // 2. dev-login → Set-Cookie(SSO) + 302 redirect_uri?code&state
        MvcResult devLogin = mvc.perform(get(devLoginUrl))
            .andExpect(status().isFound())
            .andExpect(header().exists("Set-Cookie"))
            .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith(REDIRECT + "?")))
            .andReturn();
        String sessionId = extractCookieValue(devLogin, ssoProperties.getCookieName());
        String code = queryParam(devLogin, "code");
        assertThat(queryParam(devLogin, "state")).isEqualTo("s1");

        // 3. /token code grant → access/id/refresh
        mvc.perform(post("/token").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("grant_type", "authorization_code").param("code", code)
                .param("redirect_uri", REDIRECT)
                .param("client_id", CLIENT_ID).param("client_secret", CLIENT_SECRET))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.access_token").isNotEmpty())
            .andExpect(jsonPath("$.id_token").isNotEmpty());

        // 4. 二次 /authorize 凭 cookie 免登直发 code
        Cookie sso = new Cookie(ssoProperties.getCookieName(), sessionId);
        mvc.perform(get("/authorize").param("client_id", CLIENT_ID)
                .param("redirect_uri", REDIRECT).param("state", "s2").cookie(sso))
            .andExpect(status().isFound())
            .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith(REDIRECT + "?")));
    }

    @Test
    void dev_login_rejects_unregistered_redirect_uri() throws Exception {
        mvc.perform(get("/api/auth/dev-login")
                .param("client_id", CLIENT_ID)
                .param("redirect_uri", "https://evil.example/cb"))
            .andExpect(status().isBadRequest());
    }
}
```

> 注：`@DirtiesContext` 因本类用带 dev-login 开关的上下文，与默认上下文缓存隔离。

- [ ] **Step 2: 跑测试验证失败**

Run: `mvn test -Dtest=DevLoginFlowIntegrationTest -q`
Expected: FAIL（`/api/auth/dev-login` 不存在 → 404）。

- [ ] **Step 3: 实现 DevLoginAppService**

Create `src/main/java/com/aieducenter/aieducenteridentity/sso/application/DevLoginAppService.java`:

```java
package com.aieducenter.aieducenteridentity.sso.application;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aieducenter.aieducenteridentity.account.domain.aggregate.Account;
import com.aieducenter.aieducenteridentity.account.domain.aggregate.Profile;
import com.aieducenter.aieducenteridentity.account.domain.repository.AccountRepository;
import com.aieducenter.aieducenteridentity.account.domain.repository.ProfileRepository;
import com.aieducenter.aieducenteridentity.sso.application.dto.SsoLoginResult;
import com.aieducenter.aieducenteridentity.sso.config.SsoProperties;
import com.aieducenter.aieducenteridentity.sso.domain.client.SsoClient;
import com.aieducenter.aieducenteridentity.sso.domain.client.SsoClientValidationService;
import com.aieducenter.aieducenteridentity.sso.domain.session.SsoSession;
import com.aieducenter.aieducenteridentity.sso.domain.session.SsoSessionRepository;

/**
 * dev 一键登应用服务（issue #16）——镜像 {@link SsoLoginAppService#loginByPassword}，但免认证、
 * 直接登 {@code SsoProperties.devLogin.accountEmail} 指向的预置测试账号。
 *
 * <p>仅在 {@code identity.sso.dev-login.enabled=true} 时注册（identity-web 登录页缺席时兜底）。
 * 仍走「建会话 → 发 code」正常流程，不直接发 token。</p>
 */
@Service
@ConditionalOnProperty(prefix = "identity.sso.dev-login", name = "enabled", havingValue = "true")
public class DevLoginAppService {

    private final SsoClientValidationService clientValidation;
    private final SsoSessionRepository sessionRepository;
    private final AuthorizationCodeAppService codeService;
    private final AccountRepository accountRepository;
    private final ProfileRepository profileRepository;
    private final SsoProperties properties;

    public DevLoginAppService(SsoClientValidationService clientValidation, SsoSessionRepository sessionRepository,
            AuthorizationCodeAppService codeService, AccountRepository accountRepository,
            ProfileRepository profileRepository, SsoProperties properties) {
        this.clientValidation = clientValidation;
        this.sessionRepository = sessionRepository;
        this.codeService = codeService;
        this.accountRepository = accountRepository;
        this.profileRepository = profileRepository;
        this.properties = properties;
    }

    /**
     * 以预置测试账号建 SSO 会话 + 发 code。
     *
     * @throws com.aieducenter.aieducenteridentity.sso.domain.error.OidcException client/redirect_uri 无效（400）
     */
    @Transactional
    public SsoLoginResult loginAsDevAccount(String clientId, String redirectUri, String state, String nonce) {
        SsoClient client = clientValidation.requireActiveClient(clientId);
        clientValidation.requireRedirectUri(client, redirectUri);

        Account account = accountRepository.findByEmail(properties.getDevLogin().getAccountEmail())
            .orElseThrow(() -> new IllegalStateException(
                "dev 测试账号未种：" + properties.getDevLogin().getAccountEmail()));
        account.ensureLoginable();

        Profile profile = profileRepository.findById(account.getId()).orElse(null);
        String nickname = profile != null ? profile.getNickname() : null;
        SsoSession session = sessionRepository.create(account.getId(), account.displayLabel(nickname));
        String redirectUrl = codeService.issueCodeAndRedirect(
            account.getId(), client, redirectUri, nonce, null, state);
        return new SsoLoginResult(session.sessionId(), redirectUrl);
    }
}
```

- [ ] **Step 4: 实现 DevLoginController**

Create `src/main/java/com/aieducenter/aieducenteridentity/sso/endpoints/controller/DevLoginController.java`:

```java
package com.aieducenter.aieducenteridentity.sso.endpoints.controller;

import java.net.URI;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.aieducenter.aieducenteridentity.sso.application.DevLoginAppService;
import com.aieducenter.aieducenteridentity.sso.application.dto.SsoLoginResult;
import com.aieducenter.aieducenteridentity.sso.endpoints.web.SsoCookieService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * dev 一键登端点（issue #16）。仅 {@code identity.sso.dev-login.enabled=true} 时注册——
 * identity-web 登录页缺席时，{@code /authorize} 无 cookie 跳本端点，自动登预置测试账号、发 code。
 * prod 永不开此开关 → 本控制器不注册。
 */
@RestController
@ConditionalOnProperty(prefix = "identity.sso.dev-login", name = "enabled", havingValue = "true")
@Tag(name = "SSO / Dev", description = "dev 一键登（仅 dev/local）")
public class DevLoginController {

    private final DevLoginAppService devLoginService;
    private final SsoCookieService cookieService;

    public DevLoginController(DevLoginAppService devLoginService, SsoCookieService cookieService) {
        this.devLoginService = devLoginService;
        this.cookieService = cookieService;
    }

    @GetMapping("/api/auth/dev-login")
    @Operation(summary = "dev 一键登", description = "登预置测试账号 → 建 SSO 会话 + 种 cookie + 发 code → 302 回 redirect_uri")
    public ResponseEntity<Void> devLogin(
            @RequestParam(value = "client_id", required = false) String clientId,
            @RequestParam(value = "redirect_uri", required = false) String redirectUri,
            @RequestParam(value = "state", required = false) String state,
            @RequestParam(value = "nonce", required = false) String nonce,
            HttpServletResponse response) {
        SsoLoginResult result = devLoginService.loginAsDevAccount(clientId, redirectUri, state, nonce);
        cookieService.setSessionCookie(response, result.sessionId());
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(result.redirectUrl())).build();
    }
}
```

- [ ] **Step 5: 跑测试验证通过**

Run: `mvn test -Dtest=DevLoginFlowIntegrationTest -q`
Expected: PASS（2 tests）。

- [ ] **Step 6: 跑全量 identity 测试确认无回归**

Run: `mvn test -q`
Expected: BUILD SUCCESS，全部测试通过（含既有 `SsoFlowIntegrationTest`、`ArchitectureTest`）。

> 若 `ArchitectureTest` 因新包 `sso.dev` 报分层违规：`DevAccountSeeder` 是 infra-ish 的启动 runner，但放 `sso.dev` 包内、依赖 domain 端口 + application，属合规应用装配；如规则报错，把它移到 `sso.application` 或按既有 `VerificationCodeAppService` 同级放置（保持六边形方向 controller/application→domain）。

- [ ] **Step 7: 提交**

```bash
git add src/main/java/com/aieducenter/aieducenteridentity/sso/application/DevLoginAppService.java \
        src/main/java/com/aieducenter/aieducenteridentity/sso/endpoints/controller/DevLoginController.java \
        src/test/java/com/aieducenter/aieducenteridentity/sso/endpoints/DevLoginFlowIntegrationTest.java
git commit -m "feat(#16): dev 一键登端点 /api/auth/dev-login（镜像 /api/auth/login、开关门控）"
```

---

## Task 4: application-local.yml 覆盖（接通 .localhost + dev-login）

**Files:**
- Modify: `src/main/resources/application-local.yml`

**Interfaces:**
- Consumes: Task 1 的 `dev-login.*`、Task 3 的 `/api/auth/dev-login` 路径。
- Produces: `local` profile 下 `/authorize` 无 cookie → 302 dev-login；redirect 白名单放行 `http://demo.localhost:3000/auth/callback`。

- [ ] **Step 1: 改 application-local.yml**

将 `src/main/resources/application-local.yml` 全文替换为：

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/aieducenter
    username: aiedu
    password: dev123
  data:
    redis:
      host: localhost
      port: 6379

spring.jpa.show-sql: true

# 本地联调（issue #16）：.localhost 多域 + dev 一键登兜登录页（identity-web 未建）
identity:
  sso:
    # identity-web 登录页缺席 → /authorize 无 cookie 跳 identity 自己的 dev 一键登
    login-page-url: http://identity.localhost:10001/api/auth/dev-login
    # 放行 demo 浏览器入口（Next 代理到 demo-backend :10010）
    stub-redirect-uris:
      - http://demo.localhost:3000/auth/callback
    dev-login:
      enabled: true
```

- [ ] **Step 2: 验证 local profile 上下文起得来**

Run: `mvn -o test -Dtest=AieducenterIdentityApplicationTest -Dspring.profiles.active=local -q`（仅冒烟，若本地无 PG/Redis 可跳过此步，依赖 Task 3 的集成测试已覆盖 dev-login 路径）
Expected: 不报配置绑定错。

- [ ] **Step 3: 提交**

```bash
git add src/main/resources/application-local.yml
git commit -m "chore(#16): application-local 接通 .localhost + dev-login 兜底"
```

---

## Task 5: .localhost 本地联调基建（docker-compose + dev-up + 手册）

**Files:**
- Create: `docker-compose.yml`
- Create: `dev-up.sh`
- Create: `docs/guide/local-sso-debugging.md`

**Interfaces:**
- Produces: `docker compose up -d` 起 PG(5432)+Redis(6379)；`dev-up.sh` 起基建 + 打印 identity/demo-backend/demo-web 三进程启动命令；手册讲清访问与验证。

- [ ] **Step 1: 写 docker-compose.yml**

Create `docker-compose.yml`（仓根，与现有 `docker-compose.prod.yml` 并列）:

```yaml
# 本地联调用：identity 依赖的 PG + Redis（issue #16）。
# demo-backend 无 DB，不在此列。demo-web 用 pnpm dev 本地跑。
services:
  postgres:
    image: postgres:16-alpine
    container_name: identity-postgres
    environment:
      POSTGRES_DB: aieducenter
      POSTGRES_USER: aiedu
      POSTGRES_PASSWORD: dev123
    ports:
      - "5432:5432"
    volumes:
      - identity-pg:/var/lib/postgresql/data
  redis:
    image: redis:7-alpine
    container_name: identity-redis
    ports:
      - "6379:6379"

volumes:
  identity-pg:
```

- [ ] **Step 2: 写 dev-up.sh**

Create `dev-up.sh`（仓根）:

```bash
#!/usr/bin/env bash
set -euo pipefail

# 本地 SSO 联调一键脚本（issue #16）。
# 起 PG+Redis，然后打印三个进程的启动命令（各自开一个终端跑）。

echo "==> 启动 PG + Redis（docker compose）..."
docker compose up -d
echo "==> 基建就绪。分别在三个终端执行："
echo
echo "  [identity]  cd $(pwd) && mvn spring-boot:run -Dspring-boot.run.profiles=local -Dspring-boot.run.jvmArguments=--enable-preview"
echo "  [bff]       cd $(pwd)/demo/demo-backend && mvn spring-boot:run -Dspring-boot.run.profiles=local -Dspring-boot.run.jvmArguments=--enable-preview"
echo "  [web]       cd $(pwd)/demo/demo-web && pnpm install && pnpm dev"
echo
echo "==> 访问 http://demo.localhost:3000 （点「登录」走完整 SSO 闭环）"
echo "==> 详见 docs/guide/local-sso-debugging.md"
```

```bash
chmod +x dev-up.sh
```

- [ ] **Step 3: 写联调手册**

Create `docs/guide/local-sso-debugging.md`:

````markdown
# 本地 SSO 联调（.localhost，issue #16）

`.localhost` 浏览器自动解析到 127.0.0.1 且 HTTP 下即 secure context（Secure cookie 能种）——免改 hosts、免证书。

## 端口分工

| 地址 | 进程 | cookie 域 |
|---|---|---|
| `identity.localhost:10001` | identity 后端（IdP + dev 一键登） | identity.localhost（SSO cookie） |
| `demo.localhost:10010` | demo-backend（BFF） | demo.localhost（业务 cookie） |
| `demo.localhost:3000` | demo-web（Next.js，代理 `/api` `/auth` → :10010） | demo.localhost |

## 启动

```bash
./dev-up.sh                      # 起 PG+Redis，打印三进程命令
# 三个终端分别跑 identity / demo-backend / demo-web（见脚本输出）
```

## 验证首次登录 + 二次免登

1. 浏览器开 `http://demo.localhost:3000` → 未登录页（显示「登录」按钮）。
2. 点「登录」→ 跳 identity `/authorize`（无 SSO cookie）→ 跳 dev 一键登 → 自动登预置账号 `demo@aieducenter.com / demo12345` → 发 code → 回 demo `/auth/callback` → BFF 服务端换 token、种 `demo_session` → 回首页显示用户。
3. **二次免登**：清 demo 业务 cookie（或换无痕窗口重跑一次完整登录后），再点「登录」→ 这次 identity 有 SSO cookie → 直接发 code → 无感进入（没再登一次）。

## 常见排错

- **cookie 没带上**：确认地址是 `*.localhost`（不是 `localhost`），且 `Secure` cookie 在 HTTPS 之外只在 secure context 生效——`.localhost` 满足。
- **redirect_uri 不匹配**：identity local 白名单是 `http://demo.localhost:3000/auth/callback`（见 `application-local.yml`）；浏览器入口必须走 :3000（Next 代理）。
- **dev 一键登没触发**：确认 identity 起的是 `local` profile（`identity.sso.dev-login.enabled=true`、`login-page-url` 指向 dev-login）。
````

- [ ] **Step 4: 提交**

```bash
git add docker-compose.yml dev-up.sh docs/guide/local-sso-debugging.md
git commit -m "chore(#16): .localhost 本地联调基建（docker-compose + dev-up + 手册）"
```

---

## Task 6: demo-backend 脚手架 + 配置（BFF 骨架）

**Files:**
- Create: `demo/demo-backend/pom.xml`
- Create: `demo/demo-backend/.gitignore`
- Create: `demo/demo-backend/src/main/java/com/aieducenter/demobff/DemoBffApplication.java`
- Create: `demo/demo-backend/src/main/resources/application.yml`
- Create: `demo/demo-backend/src/main/resources/application-local.yml`
- Create: `demo/demo-backend/src/main/java/com/aieducenter/demobff/config/SsoProperties.java`

**Interfaces:**
- Produces: 可 `mvn compile` 的 demo-backend 工程；`SsoProperties`（`sso.*`）绑 issuer/clientId/secret/redirectUri/scope/appBaseUrl。

- [ ] **Step 1: 写 pom.xml**

Create `demo/demo-backend/pom.xml`（cartisan-boot 父、剪到只留 web + nimbus + cartisan-web）:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.cartisan</groupId>
        <artifactId>cartisan-boot</artifactId>
        <version>0.1.0-SNAPSHOT</version>
    </parent>

    <groupId>com.aieducenter</groupId>
    <artifactId>aieducenter-demo-bff</artifactId>
    <version>1.0.0-SNAPSHOT</version>

    <properties>
        <maven.compiler.source>21</maven.compiler.source>
        <maven.compiler.target>21</maven.compiler.target>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>

        <!-- cartisan-web：统一响应体 + 全局异常（标准 cartisan-boot 项目姿态） -->
        <dependency>
            <groupId>com.cartisan</groupId>
            <artifactId>cartisan-web</artifactId>
            <version>0.1.0-SNAPSHOT</version>
        </dependency>

        <!-- 解 id_token claims（不验签：token 经可信机机通道从 identity /token 取得，#17 再加 /jwks 验签） -->
        <dependency>
            <groupId>com.nimbusds</groupId>
            <artifactId>nimbus-jose-jwt</artifactId>
            <version>10.9.1</version>
        </dependency>

        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <scope>provided</scope>
        </dependency>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <configuration>
                    <source>21</source>
                    <target>21</target>
                    <compilerArgs>
                        <arg>--enable-preview</arg>
                    </compilerArgs>
                </configuration>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
                <configuration>
                    <argLine>--enable-preview --add-opens java.base/java.lang=ALL-UNNAMED</argLine>
                </configuration>
            </plugin>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <version>3.4.1</version>
                <configuration>
                    <mainClass>com.aieducenter.demobff.DemoBffApplication</mainClass>
                    <jvmArguments>--enable-preview</jvmArguments>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 2: 写主类 + .gitignore**

Create `demo/demo-backend/src/main/java/com/aieducenter/demobff/DemoBffApplication.java`:

```java
package com.aieducenter.demobff;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * demo 消费方 BFF（issue #16）——OIDC Authorization Code Flow 消费方参考实现。
 * token 只存服务端内存会话，浏览器只持业务 cookie。
 */
@SpringBootApplication
public class DemoBffApplication {
    public static void main(String[] args) {
        SpringApplication.run(DemoBffApplication.class, args);
    }
}
```

Create `demo/demo-backend/.gitignore`:

```
target/
*.class
*.jar
.idea/
*.iml
.DS_Store
```

- [ ] **Step 3: 写 SsoProperties**

Create `demo/demo-backend/src/main/java/com/aieducenter/demobff/config/SsoProperties.java`:

```java
package com.aieducenter.demobff.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * demo BFF 的 SSO/OIDC 消费方配置（{@code sso.*}）。
 */
@Component
@ConfigurationProperties(prefix = "sso")
public class SsoProperties {

    /** identity IdP 地址（local：http://identity.localhost:10001）。 */
    private String issuer;
    /** 消费方 client_id（identity stub：demo-client）。 */
    private String clientId;
    /** 消费方 client_secret（BFF 服务端用，浏览器不接触）。 */
    private String clientSecret;
    /** 回调地址（浏览器入口，经 Next 代理到本 BFF）。 */
    private String redirectUri;
    /** 授权范围。 */
    private String scope = "openid profile";
    /** 前端地址（登录/登出后回跳）。 */
    private String appBaseUrl;

    public String getIssuer() { return issuer; }
    public void setIssuer(String issuer) { this.issuer = issuer; }
    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }
    public String getClientSecret() { return clientSecret; }
    public void setClientSecret(String clientSecret) { this.clientSecret = clientSecret; }
    public String getRedirectUri() { return redirectUri; }
    public void setRedirectUri(String redirectUri) { this.redirectUri = redirectUri; }
    public String getScope() { return scope; }
    public void setScope(String scope) { this.scope = scope; }
    public String getAppBaseUrl() { return appBaseUrl; }
    public void setAppBaseUrl(String appBaseUrl) { this.appBaseUrl = appBaseUrl; }
}
```

- [ ] **Step 4: 写 application.yml + application-local.yml**

Create `demo/demo-backend/src/main/resources/application.yml`:

```yaml
server:
  port: 10010

spring:
  application:
    name: aieducenter-demo-bff
  profiles:
    active: ${SPRING_PROFILES_ACTIVE:local}

management:
  endpoints:
    web:
      exposure:
        include: health
```

Create `demo/demo-backend/src/main/resources/application-local.yml`:

```yaml
sso:
  issuer: http://identity.localhost:10001
  client-id: demo-client
  client-secret: demo-secret-please-change
  redirect-uri: http://demo.localhost:3000/auth/callback
  scope: openid profile
  app-base-url: http://demo.localhost:3000
```

- [ ] **Step 5: 编译验证**

Run: `cd demo/demo-backend && mvn -o compile -q && cd ../..`
Expected: BUILD SUCCESS（确认 parent/依赖在本地 .m2 可用）。

- [ ] **Step 6: 提交**

```bash
git add demo/demo-backend/
git commit -m "feat(#16): demo-backend BFF 脚手架 + SSO 配置"
```

---

## Task 7: BFF 会话存储 + OidcClient（OIDC 客户端核心）

**Files:**
- Create: `demo/demo-backend/src/main/java/com/aieducenter/demobff/sso/BffSession.java`
- Create: `demo/demo-backend/src/main/java/com/aieducenter/demobff/sso/BffSessionStore.java`
- Create: `demo/demo-backend/src/main/java/com/aieducenter/demobff/sso/TokenResponse.java`
- Create: `demo/demo-backend/src/main/java/com/aieducenter/demobff/sso/OidcClient.java`
- Create: `demo/demo-backend/src/test/java/com/aieducenter/demobff/sso/OidcClientTest.java`

**Interfaces:**
- Consumes: `SsoProperties`（Task 6）、Spring `RestClient.Builder`（自动配置）。
- Produces:
  - `BffSession(String accessToken, String idToken, String refreshToken)`
  - `BffSessionStore.put(sid,session)` / `get(sid)` / `remove(sid)`（内存 `ConcurrentHashMap`）
  - `TokenResponse(accessToken, tokenType, expiresIn, refreshToken, idToken)`（Jackson 映射 identity 的 snake_case）
  - `OidcClient.authorizeUrl(state,nonce)` / `exchangeCode(code)` / `decodeIdToken(idToken)` / `randomToken()`

- [ ] **Step 1: 写失败测试**

Create `demo/demo-backend/src/test/java/com/aieducenter/demobff/sso/OidcClientTest.java`:

```java
package com.aieducenter.demobff.sso;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.aieducenter.demobff.config.SsoProperties;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;

class OidcClientTest {

    private SsoProperties props(String issuer) {
        SsoProperties p = new SsoProperties();
        p.setIssuer(issuer);
        p.setClientId("demo-client");
        p.setClientSecret("demo-secret-please-change");
        p.setRedirectUri("http://demo.localhost:3000/auth/callback");
        p.setScope("openid profile");
        return p;
    }

    @Test
    void authorizeUrl_contains_required_params() {
        OidcClient client = new OidcClient(props("http://idp"), RestClient.builder());
        String url = client.authorizeUrl("st", "nc");
        assertThat(url).startsWith("http://idp/authorize?");
        assertThat(url).contains("client_id=demo-client");
        assertThat(url).contains("response_type=code");
        assertThat(url).contains("state=st").contains("nonce=nc");
    }

    @Test
    void exchangeCode_parses_token_response() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(once(), requestTo("http://idp/token"))
            .andRespond(withSuccess(
                "{\"access_token\":\"a\",\"token_type\":\"Bearer\",\"expires_in\":900,"
                + "\"refresh_token\":\"r\",\"id_token\":\"\"}",
                MediaType.APPLICATION_JSON));

        TokenResponse t = new OidcClient(props("http://idp"), builder).exchangeCode("code1");
        server.verify();
        assertThat(t.accessToken()).isEqualTo("a");
        assertThat(t.refreshToken()).isEqualTo("r");
    }

    @Test
    void decodeIdToken_reads_claims() throws Exception {
        String idToken = new PlainJWT(new JWTClaimsSet.Builder()
            .subject("u1").claim("email", "a@b.c").claim("nickname", "A").claim("picture", "p")
            .build()).serialize();
        OidcClient client = new OidcClient(props("http://idp"), RestClient.builder());
        TokenResponse t = new TokenResponse("a", "Bearer", 900L, "r", idToken);
        var claims = client.decodeIdToken(t.idToken());
        assertThat(claims.subject()).isEqualTo("u1");
        assertThat(claims.email()).isEqualTo("a@b.c");
        assertThat(claims.nickname()).isEqualTo("A");
    }

    @Test
    void randomToken_is_unique() {
        OidcClient client = new OidcClient(props("http://idp"), RestClient.builder());
        assertThat(OidcClient.randomToken()).isNotEqualTo(OidcClient.randomToken());
    }
}
```

- [ ] **Step 2: 跑测试验证失败**

Run: `cd demo/demo-backend && mvn -o test -Dtest=OidcClientTest -q && cd ../..`
Expected: FAIL（类不存在，编译失败）。

- [ ] **Step 3: 实现 BffSession + BffSessionStore + TokenResponse**

Create `demo/demo-backend/src/main/java/com/aieducenter/demobff/sso/BffSession.java`:

```java
package com.aieducenter.demobff.sso;

/**
 * BFF 服务端会话——存换到的 token 三件套（浏览器永不接触）。
 */
public record BffSession(String accessToken, String idToken, String refreshToken) {
}
```

Create `demo/demo-backend/src/main/java/com/aieducenter/demobff/sso/BffSessionStore.java`:

```java
package com.aieducenter.demobff.sso;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * BFF 内存会话存储（demo 用，进程重启即丢）。key = 不透明业务 sessionId（业务 cookie 值）。
 */
@Component
public class BffSessionStore {

    private final ConcurrentHashMap<String, BffSession> store = new ConcurrentHashMap<>();

    public void put(String sessionId, BffSession session) {
        store.put(sessionId, session);
    }

    public Optional<BffSession> get(String sessionId) {
        return Optional.ofNullable(store.get(sessionId));
    }

    public void remove(String sessionId) {
        store.remove(sessionId);
    }
}
```

Create `demo/demo-backend/src/main/java/com/aieducenter/demobff/sso/TokenResponse.java`:

```java
package com.aieducenter.demobff.sso;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * identity {@code /token} 响应（OIDC 标准 snake_case；显式映射，与全局命名策略无关）。
 */
public record TokenResponse(
    @JsonProperty("access_token") String accessToken,
    @JsonProperty("token_type") String tokenType,
    @JsonProperty("expires_in") Long expiresIn,
    @JsonProperty("refresh_token") String refreshToken,
    @JsonProperty("id_token") String idToken
) {
}
```

- [ ] **Step 4: 实现 OidcClient**

Create `demo/demo-backend/src/main/java/com/aieducenter/demobff/sso/OidcClient.java`:

```java
package com.aieducenter.demobff.sso;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClient.Builder;

import com.aieducenter.demobff.config.SsoProperties;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.JWTParser;

/**
 * OIDC 客户端核心（issue #16）：构造 /authorize URL、用 code 换 token（服务端带 client_secret）、解 id_token。
 */
@Component
public class OidcClient {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final SsoProperties props;
    private final RestClient tokenClient;

    public OidcClient(SsoProperties props, Builder restClientBuilder) {
        this.props = props;
        this.tokenClient = restClientBuilder.baseUrl(props.getIssuer()).build();
    }

    /** 构造 /authorize 跳转 URL（带 state/nonce 防 CSRF/重放）。 */
    public String authorizeUrl(String state, String nonce) {
        return props.getIssuer() + "/authorize"
            + "?client_id=" + enc(props.getClientId())
            + "&redirect_uri=" + enc(props.getRedirectUri())
            + "&response_type=code"
            + "&scope=" + enc(props.getScope())
            + "&state=" + enc(state)
            + "&nonce=" + enc(nonce);
    }

    /** 用 code + client_secret 服务端换 token（grant_type=authorization_code）。 */
    public TokenResponse exchangeCode(String code) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("redirect_uri", props.getRedirectUri());
        form.add("client_id", props.getClientId());
        form.add("client_secret", props.getClientSecret());
        return tokenClient.post().uri("/token")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(form)
            .retrieve().body(TokenResponse.class);
    }

    /** id_token 的用户声明（不验签：经可信机机通道取得；#17 引入 /jwks 后补验签）。 */
    public IdTokenClaims decodeIdToken(String idToken) {
        try {
            JWTClaimsSet c = JWTParser.parse(idToken).getJWTClaimsSet();
            return new IdTokenClaims(
                c.getSubject(),
                c.getStringClaim("email"),
                c.getStringClaim("nickname"),
                c.getStringClaim("picture"));
        } catch (Exception e) {
            throw new IllegalStateException("id_token 解析失败", e);
        }
    }

    /** 不透明随机串（业务 sessionId / state / nonce 共用）。 */
    public static String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String enc(String v) {
        return URLEncoder.encode(v, StandardCharsets.UTF_8);
    }

    /** id_token 解出的用户信息。 */
    public record IdTokenClaims(String subject, String email, String nickname, String picture) {
    }
}
```

- [ ] **Step 5: 跑测试验证通过**

Run: `cd demo/demo-backend && mvn -o test -Dtest=OidcClientTest -q && cd ../..`
Expected: PASS（4 tests）。

- [ ] **Step 6: 提交**

```bash
git add demo/demo-backend/
git commit -m "feat(#16): demo-backend 内存会话 + OIDC 客户端（authorize/token/解 id_token）"
```

---

## Task 8: AuthController + MeController（BFF 端点）

**Files:**
- Create: `demo/demo-backend/src/main/java/com/aieducenter/demobff/sso/AuthController.java`
- Create: `demo/demo-backend/src/main/java/com/aieducenter/demobff/sso/MeController.java`
- Create: `demo/demo-backend/src/test/java/com/aieducenter/demobff/sso/AuthControllerTest.java`
- Create: `demo/demo-backend/src/test/java/com/aieducenter/demobff/sso/MeControllerTest.java`

**Interfaces:**
- Consumes: `OidcClient`、`BffSessionStore`、`SsoProperties`（Task 6/7）。
- Produces：
  - `GET /auth/login` → 种 `oauth_txn` cookie + 302 identity `/authorize`。
  - `GET /auth/callback?code&state` → 校 state → `exchangeCode` → 存会话 → 种 `demo_session` → 清 `oauth_txn` → 302 回前端 `/`。
  - `POST /auth/logout` → 删会话 + 清 `demo_session` → 302 回前端 `/`。
  - `GET /api/me` → 凭 `demo_session` 解 id_token claims 返 `{userId,email,nickname,picture}`，无则 401。

- [ ] **Step 1: 写失败测试 AuthControllerTest**

Create `demo/demo-backend/src/test/java/com/aieducenter/demobff/sso/AuthControllerTest.java`:

```java
package com.aieducenter.demobff.sso;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.aieducenter.demobff.config.SsoProperties;

import jakarta.servlet.http.Cookie;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock OidcClient oidcClient;
    @Mock BffSessionStore sessionStore;
    @InjectMocks AuthController controller;

    private SsoProperties props;
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        props = new SsoProperties();
        props.setIssuer("http://idp");
        props.setRedirectUri("http://demo.localhost:3000/auth/callback");
        props.setAppBaseUrl("http://demo.localhost:3000");
        controller.props = props;  // AuthController 包级可见字段，便于测试注入
        mvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void login_sets_txn_cookie_and_redirects_to_authorize() throws Exception {
        when(oidcClient.authorizeUrl(any(), any())).thenReturn("http://idp/authorize?state=st");
        mvc.perform(get("/auth/login"))
            .andExpect(status().isFound())
            .andExpect(header().string("Location", "http://idp/authorize?state=st"))
            .andExpect(cookie().exists("oauth_txn"));
    }

    @Test
    void callback_exchanges_code_and_sets_session_cookie() throws Exception {
        when(oidcClient.exchangeCode("c1")).thenReturn(
            new TokenResponse("a", "Bearer", 900L, "r", "IDT"));
        when(oidcClient.decodeIdToken("IDT")).thenReturn(
            new OidcClient.IdTokenClaims("u1", "a@b.c", "A", null));

        String txn = "st:nc";
        MvcResult result = mvc.perform(get("/auth/callback")
                .param("code", "c1").param("state", "st")
                .cookie(new Cookie("oauth_txn", txn)))
            .andExpect(status().isFound())
            .andExpect(header().string("Location", "http://demo.localhost:3000/"))
            .andExpect(cookie().exists("demo_session"))
            .andReturn();
        assertThat(result.getResponse().getCookie("oauth_txn").getMaxAge()).isZero();
    }

    @Test
    void callback_state_mismatch_redirects_with_error() throws Exception {
        mvc.perform(get("/auth/callback")
                .param("code", "c1").param("state", "evil")
                .cookie(new Cookie("oauth_txn", "st:nc")))
            .andExpect(status().isFound())
            .andExpect(header().string("Location",
                org.hamcrest.Matchers.containsString("error=state_mismatch")));
    }

    @Test
    void logout_clears_session_cookie() throws Exception {
        mvc.perform(post("/auth/logout").cookie(new Cookie("demo_session", "sid")))
            .andExpect(status().isFound())
            .andExpect(header().string("Location", "http://demo.localhost:3000/"))
            .andExpect(cookie().exists("demo_session"));
    }
}
```

> 实现侧 `AuthController.props` 设为包级可见字段（便于此测试注入 `SsoProperties`）。

- [ ] **Step 2: 写失败测试 MeControllerTest**

Create `demo/demo-backend/src/test/java/com/aieducenter/demobff/sso/MeControllerTest.java`:

```java
package com.aieducenter.demobff.sso;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class MeControllerTest {

    @Mock OidcClient oidcClient;
    @Mock BffSessionStore sessionStore;
    @InjectMocks MeController controller;

    private MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void me_without_cookie_is_401() throws Exception {
        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void me_returns_claims_for_valid_session() throws Exception {
        when(sessionStore.get("sid")).thenReturn(Optional.of(new BffSession("a", "IDT", "r")));
        when(oidcClient.decodeIdToken("IDT")).thenReturn(
            new OidcClient.IdTokenClaims("u1", "a@b.c", "A", "pic"));

        mvc.perform(get("/api/me").cookie(new jakarta.servlet.http.Cookie("demo_session", "sid")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.userId").value("u1"))
            .andExpect(jsonPath("$.email").value("a@b.c"))
            .andExpect(jsonPath("$.nickname").value("A"));
    }
}
```

- [ ] **Step 3: 跑测试验证失败**

Run: `cd demo/demo-backend && mvn -o test -Dtest='AuthControllerTest,MeControllerTest' -q && cd ../..`
Expected: FAIL（控制器不存在）。

- [ ] **Step 4: 实现 AuthController**

Create `demo/demo-backend/src/main/java/com/aieducenter/demobff/sso/AuthController.java`:

```java
package com.aieducenter.demobff.sso;

import java.net.URI;
import java.util.Map;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.aieducenter.demobff.config.SsoProperties;

/**
 * demo BFF 认证端点（issue #16）：/auth/login 发起、/auth/callback 换 token+种业务 cookie、/auth/logout。
 * token 只存服务端会话；浏览器只持 demo_session 业务 cookie。
 */
@RestController
public class AuthController {

    private static final int TXN_MAX_AGE = 600;

    final SsoProperties props;  // 包级可见，便于 standalone 测试注入
    private final OidcClient oidcClient;
    private final BffSessionStore sessionStore;

    public AuthController(SsoProperties props, OidcClient oidcClient, BffSessionStore sessionStore) {
        this.props = props;
        this.oidcClient = oidcClient;
        this.sessionStore = sessionStore;
    }

    @GetMapping("/auth/login")
    public ResponseEntity<Void> login(HttpServletResponse response) {
        String state = OidcClient.randomToken();
        String nonce = OidcClient.randomToken();
        response.addHeader("Set-Cookie", txnCookie(state + ":" + nonce, TXN_MAX_AGE).toString());
        return ResponseEntity.status(HttpStatus.FOUND)
            .location(URI.create(oidcClient.authorizeUrl(state, nonce))).build();
    }

    @GetMapping("/auth/callback")
    public ResponseEntity<Void> callback(@RequestParam("code") String code,
            @RequestParam(value = "state", required = false) String state,
            @CookieValue(value = "oauth_txn", required = false) String txn,
            HttpServletResponse response) {
        if (txn == null || state == null || !txn.startsWith(state + ":")) {
            return redirect(props.getAppBaseUrl() + "/?error=state_mismatch");
        }
        TokenResponse tokens = oidcClient.exchangeCode(code);
        String sessionId = OidcClient.randomToken();
        sessionStore.put(sessionId, new BffSession(tokens.accessToken(), tokens.idToken(), tokens.refreshToken()));
        response.addHeader("Set-Cookie", sessionCookie(sessionId).toString());
        response.addHeader("Set-Cookie", txnCookie("", 0).toString()); // 清 oauth_txn
        return redirect(props.getAppBaseUrl() + "/");
    }

    @PostMapping("/auth/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        Cookie existing = findCookie(request, "demo_session");
        if (existing != null) {
            sessionStore.remove(existing.getValue());
        }
        response.addHeader("Set-Cookie", sessionCookie("").maxAge(0).toString());
        return redirect(props.getAppBaseUrl() + "/");
    }

    private static ResponseEntity<Void> redirect(String location) {
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(location)).build();
    }

    private static Cookie findCookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie c : cookies) {
            if (name.equals(c.getName())) return c;
        }
        return null;
    }

    private ResponseCookie.ResponseCookieBuilder txnCookie(String value, long maxAge) {
        return ResponseCookie.from("oauth_txn", value)
            .httpOnly(true).secure(true).sameSite("Lax").path("/").maxAge(maxAge);
    }

    private ResponseCookie.ResponseCookieBuilder sessionCookie(String value) {
        return ResponseCookie.from("demo_session", value)
            .httpOnly(true).secure(true).sameSite("Lax").path("/");
    }
}
```

- [ ] **Step 5: 实现 MeController**

Create `demo/demo-backend/src/main/java/com/aieducenter/demobff/sso/MeController.java`:

```java
package com.aieducenter.demobff.sso;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * demo BFF /api/me（issue #16）：凭业务 cookie 取会话 → 解 id_token claims 返当前用户。
 * 无有效会话 → 401。
 */
@RestController
public class MeController {

    private final OidcClient oidcClient;
    private final BffSessionStore sessionStore;

    public MeController(OidcClient oidcClient, BffSessionStore sessionStore) {
        this.oidcClient = oidcClient;
        this.sessionStore = sessionStore;
    }

    @GetMapping("/api/me")
    public ResponseEntity<Map<String, String>> me(@CookieValue(value = "demo_session", required = false) String sid) {
        if (sid == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return sessionStore.get(sid)
            .map(s -> {
                OidcClient.IdTokenClaims c = oidcClient.decodeIdToken(s.idToken());
                return ResponseEntity.ok(Map.of(
                    "userId", n(c.subject()),
                    "email", n(c.email()),
                    "nickname", n(c.nickname()),
                    "picture", n(c.picture())));
            })
            .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    private static String n(String v) {
        return v == null ? "" : v;
    }
}
```

- [ ] **Step 6: 跑测试验证通过**

Run: `cd demo/demo-backend && mvn -o test -q && cd ../..`
Expected: PASS（全部 demo-backend 测试）。

- [ ] **Step 7: 提交**

```bash
git add demo/demo-backend/
git commit -m "feat(#16): demo-backend /auth/login+/callback+/logout + /api/me"
```

---

## Task 9: demo-web 脚手架 + 页面（Next.js 显示页）

**Files:**
- Create: `demo/demo-web/package.json`
- Create: `demo/demo-web/next.config.mjs`
- Create: `demo/demo-web/tsconfig.json`
- Create: `demo/demo-web/src/app/layout.tsx`
- Create: `demo/demo-web/src/app/page.tsx`
- Create: `demo/demo-web/src/lib/api.ts`
- 取自 cartisan-boot 前端脚手架默认：`tailwind.config.ts`、`postcss.config.mjs`、`src/app/globals.css`、`.eslintrc.json`、`src/lib/utils.ts`（从 `cartisan-boot/scripts/create-project.sh` 的 `generate_frontend` 产出复制）。

**Interfaces:**
- Consumes: demo-backend `/api/me`、`/auth/login`、`/auth/logout`（Task 8）。
- Produces: `http://demo.localhost:3000/` —— 未登录显示登录链接，登录后显示用户 + 登出按钮。

- [ ] **Step 1: 写 package.json**

Create `demo/demo-web/package.json`:

```json
{
  "name": "aieducenter-demo-web",
  "version": "1.0.0",
  "private": true,
  "scripts": {
    "dev": "next dev -p 3000",
    "build": "next build",
    "start": "next start",
    "lint": "next lint",
    "typecheck": "tsc --noEmit"
  },
  "dependencies": {
    "next": "^15.0.0",
    "react": "^19.0.0",
    "react-dom": "^19.0.0"
  },
  "devDependencies": {
    "@types/node": "^20.0.0",
    "@types/react": "^19.0.0",
    "@types/react-dom": "^19.0.0",
    "autoprefixer": "^10.4.27",
    "eslint": "^10.0.3",
    "eslint-config-next": "^16.1.6",
    "postcss": "^8.4.47",
    "tailwindcss": "3.4.19",
    "typescript": "^5.0.0"
  }
}
```

- [ ] **Step 2: 写 next.config.mjs（rewrite /api、/auth → backend）**

Create `demo/demo-web/next.config.mjs`:

```javascript
/** @type {import('next').NextConfig} */
const nextConfig = {
  reactStrictMode: true,
  output: 'standalone',
  rewrites: async () => {
    const backendUrl = process.env.BACKEND_URL || 'http://demo.localhost:10010'
    return [
      { source: '/api/:path*', destination: `${backendUrl}/api/:path*` },
      { source: '/auth/:path*', destination: `${backendUrl}/auth/:path*` },
    ]
  },
}

export default nextConfig
```

- [ ] **Step 3: 写 tsconfig.json + 复制脚手架默认文件**

Create `demo/demo-web/tsconfig.json`（与 cartisan-boot 前端脚手架一致）:

```json
{
  "compilerOptions": {
    "target": "ES2022",
    "lib": ["ES2023", "DOM", "DOM.Iterable"],
    "module": "ESNext",
    "moduleResolution": "bundler",
    "resolveJsonModule": true,
    "allowJs": true,
    "strict": true,
    "skipLibCheck": true,
    "esModuleInterop": true,
    "allowSyntheticDefaultImports": true,
    "forceConsistentCasingInFileNames": true,
    "noEmit": true,
    "incremental": true,
    "isolatedModules": true,
    "jsx": "preserve",
    "plugins": [{ "name": "next" }],
    "baseUrl": ".",
    "paths": { "@/*": ["./src/*"] }
  },
  "include": ["next-env.d.ts", "**/*.ts", "**/*.tsx", ".next/types/**/*.ts"],
  "exclude": ["node_modules"]
}
```

从 `cartisan-boot/scripts/create-project.sh` 的 `generate_frontend` 段（已在仓外 `/Users/zhangcolin/workspace/cartisan-boot/`）复制以下默认文件到 `demo/demo-web/`：`tailwind.config.ts`、`postcss.config.mjs`、`.eslintrc.json`、`src/app/globals.css`、`src/lib/utils.ts`。

- [ ] **Step 4: 写 layout.tsx**

Create `demo/demo-web/src/app/layout.tsx`:

```tsx
import './globals.css'

export const metadata = {
  title: 'demo 消费方（SSO 参考实现）',
}

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="zh-CN">
      <body>{children}</body>
    </html>
  )
}
```

- [ ] **Step 5: 写 api.ts + page.tsx**

Create `demo/demo-web/src/lib/api.ts`:

```typescript
export type CurrentUser = {
  userId: string
  email: string
  nickname: string
  picture: string
}

export async function fetchMe(cookie?: string): Promise<CurrentUser | null> {
  const headers: Record<string, string> = {}
  if (cookie) headers.cookie = cookie
  const res = await fetch('/api/me', { headers, cache: 'no-store' })
  if (!res.ok) return null
  return res.json()
}
```

Create `demo/demo-web/src/app/page.tsx`:

```tsx
import { fetchMe, type CurrentUser } from '@/lib/api'

export default async function Home() {
  let user: CurrentUser | null = null
  try {
    user = await fetchMe()
  } catch {
    user = null
  }

  if (!user) {
    return (
      <main className="flex min-h-screen flex-col items-center justify-center gap-4">
        <h1 className="text-3xl font-bold">demo 消费方</h1>
        <p className="text-gray-600">未登录</p>
        <a href="/auth/login" className="rounded bg-blue-600 px-4 py-2 text-white hover:bg-blue-700">
          登录（跳 identity SSO）
        </a>
      </main>
    )
  }

  return (
    <main className="flex min-h-screen flex-col items-center justify-center gap-4">
      <h1 className="text-3xl font-bold">已登录</h1>
      <dl className="text-center">
        <dt className="text-gray-500">userId</dt><dd className="font-mono">{user.userId}</dd>
        <dt className="text-gray-500">nickname</dt><dd>{user.nickname}</dd>
        <dt className="text-gray-500">email</dt><dd>{user.email}</dd>
      </dl>
      <form action="/auth/logout" method="post">
        <button type="submit" className="rounded border px-4 py-2 hover:bg-gray-100">登出</button>
      </form>
    </main>
  )
}
```

> 说明：`page.tsx` 是 server component，`fetch('/api/me')` 在服务端走 Next rewrite 到 demo-backend。登出用 form POST（Next 原生支持，提交到 `/auth/logout` 走 rewrite）。

- [ ] **Step 6: typecheck + build 验证**

Run:
```bash
cd demo/demo-web && pnpm install && pnpm typecheck && cd ../..
```
Expected: typecheck 通过（无 TS 错误）。

- [ ] **Step 7: 提交**

```bash
git add demo/demo-web/
git commit -m "feat(#16): demo-web 显示页（/api/me + 登录/登出，Next rewrite 代理 BFF）"
```

---

## Task 10: 收尾——identity-web 协调 issue + CONTEXT.md 更新 + 文档化端口

**Files:**
- Modify: `CONTEXT.md`（「开发测试」段落补 .localhost 端口与 dev-login 兜底）。

**Interfaces:**
- 无代码；产出：identity-web 仓的协调 issue + 本仓 CONTEXT 更新。

- [ ] **Step 1: 给 identity-web 发协调 issue**

Run:
```bash
gh issue create --repo ZhangColin/aieducenter-identity-web \
  --title "SSO 登录页：与 identity 后端契约对齐（dev 一键登兜底已就位）" \
  --body "$(cat <<'EOF'
identity 后端 #14/#15/#16 已把 SSO 闭环收敛到这套契约。本 issue 把 identity-web 登录页要对接的点交代清楚，等前端开工照此接。

## identity 侧已就绪
- \`GET /authorize\`：校 client_id/redirect_uri → 看 SSO cookie。有 → 发 code 302 回 redirect_uri；无 → 302 到 \`identity.sso.login-page-url\`（透传 client_id/redirect_uri/state/nonce）。
- \`POST /api/auth/login\`：body \`{clientId, redirectUri, state, nonce, account, password}\` → 验凭据 → 建 SSO 会话 + 种 SSO cookie + 发 code → 302 回 \`redirect_uri?code&state\`。
- SSO cookie：名 \`sso_session\`，httpOnly+Secure+SameSite-Lax，值=不透明 sessionId（非 token）。
- \`/token\`：code 换 access/id/refresh（机机，带 client_secret，前端不调）。

## 本地联调（identity-web 缺席期间的兜底）
- 本期（#16）在 identity 加了 dev 一键登 \`GET /api/auth/dev-login\`，仅 \`identity.sso.dev-login.enabled=true\`（local profile）注册。\`/authorize\` 无 cookie 时跳它，自动登预置测试账号 \`demo@aieducenter.com / demo12345\`，让闭环现在就能跑通。
- identity-web 登录页落地后，只需把 local profile 的 \`identity.sso.login-page-url\` 从 dev-login 换回登录页 URL（prod 本就指向登录页）。即：identity-web 替换 dev-login，零返工。

## identity-web 登录页要做
1. 读 \`/authorize\` 透传的 client_id/redirect_uri/state/nonce（query），渲染「登录到 XXX 应用」。
2. 提交 \`POST /api/auth/login\`（上字段）；失败留页显示，不回业务应用。
3. 登录成功由 identity 302 回 redirect_uri（identity-web 自己不用换 token）。

参考：demo 消费方（\`aieducenter-identity/demo/\`）+ \`docs/guide/local-sso-debugging.md\`。
EOF
)"
```
Expected: 输出新 issue URL（记下来）。

- [ ] **Step 2: 更新 CONTEXT.md「开发测试」段落**

在 `CONTEXT.md`「## 开发测试」段，把本地那段改为含端口与 dev-login 兜底：

将：
```
- **本地**：`.localhost` 多域（identity.localhost + demo.localhost）——浏览器自动解析到 127.0.0.1 + 当 secure context，免改 hosts、免证书。
- **demo 消费方**（demo.localhost）：发起 /authorize + 收 callback + BFF 换 token + 显示用户。一身三任：**测试必需品 + 对接活示例 + 演示开发姿态**。
```
替换为：
```
- **本地**：`.localhost` 多域——浏览器自动解析到 127.0.0.1 + 当 secure context（免改 hosts、免证书）。端口：identity `identity.localhost:10001`、demo BFF `demo.localhost:10010`、demo-web `demo.localhost:3000`（Next 代理 `/api`·`/auth`→BFF）。一键起：`./dev-up.sh`（PG+Redis+三进程命令）；手册 `docs/guide/local-sso-debugging.md`。
- **demo 消费方**（仓内 `demo/`：`demo-backend` BFF + `demo-web`）：发起 /authorize + 收 callback + BFF 换 token（内存存、浏览器不接触）+ 显示用户。一身三任：**测试必需品 + 对接活示例 + 演示开发姿态**。
- **dev 一键登**（identity-web 登录页缺席时的兜底，#16）：\`identity.sso.dev-login.enabled=true\`（local profile）时，\`/authorize\` 无 cookie 跳 \`/api/auth/dev-login\`，自动登预置账号 \`demo@aieducenter.com\`、发 code。仍走正常 code→/token 流程（不直接发 token）。identity-web 登录页落地后，把 local 的 \`login-page-url\` 换回登录页即可。
```

- [ ] **Step 3: 提交**

```bash
git add CONTEXT.md
git commit -m "docs(#16): CONTEXT 开发测试段补 .localhost 端口 + dev-login 兜底"
```

---

## Task 11: 全量验证 + 收尾

- [ ] **Step 1: identity 全量测试**

Run: `mvn test -q`
Expected: BUILD SUCCESS，全部通过（含新 `SsoPropertiesTest`、`DevAccountSeederTest`、`DevLoginFlowIntegrationTest`、既有 SSO/account/verification + `ArchitectureTest`）。

- [ ] **Step 2: demo-backend 全量测试**

Run: `cd demo/demo-backend && mvn -o test -q && cd ../..`
Expected: BUILD SUCCESS（`OidcClientTest`、`AuthControllerTest`、`MeControllerTest`）。

- [ ] **Step 3: demo-web typecheck/lint**

Run: `cd demo/demo-web && pnpm typecheck && pnpm lint && cd ../..`
Expected: 无错。

- [ ] **Step 4: 浏览器真跑通（spec 验收，手动）**

按 `docs/guide/local-sso-debugging.md`：
1. `./dev-up.sh` + 三终端起 identity / demo-backend / demo-web。
2. 访问 `http://demo.localhost:3000` → 点登录 → 自动经 dev-login → 回首页显示 `demo@aieducenter.com` 用户。
3. **二次免登**：再点登录 → identity 有 SSO cookie → 直接发 code → 无感进入。

- [ ] **Step 5: 关闭 issue #16（验证通过后）**

Run: `gh issue close 16 --comment "实现见上述 commits + docs/guide/local-sso-debugging.md；identity-web 协调见其仓 issue。"`

---

## Self-Review（写完后自查记录）

- **Spec 覆盖**：dev 一键登（Task 1–3）✓、demo BFF 换 token+存服务端+种业务 cookie（Task 7/8）✓、二次免登（Task 3 测 + Task 11 手动）✓、.localhost 基建（Task 5）✓、dev 测试账号一键登（Task 2/3）✓、id_token 取用户（#17 未建的范围边界，Task 7/8）✓。
- **占位符扫描**：无 TBD/TODO；脚手架默认文件指向 cartisan-boot 既有产出（具体路径），非占位。
- **类型一致**：`SsoProperties.getDevLogin()`（Task1）→ Task2/3 一致；`BffSession(accessToken,idToken,refreshToken)`（Task7）→ Task8 一致；`OidcClient.IdTokenClaims(subject,email,nickname,picture)`（Task7）→ Task8 一致；`TokenResponse` snake_case 映射（Task7）→ identity `/token` 实际输出（`SsoFlowIntegrationTest` 已证 `$.access_token`）一致。
