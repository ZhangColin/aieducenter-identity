package com.aieducenter.aieducenteridentity.test;

import java.util.Objects;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.aieducenter.aieducenteridentity.account.domain.aggregate.Account;
import com.aieducenter.aieducenteridentity.account.domain.repository.AccountRepository;
import com.aieducenter.aieducenteridentity.account.domain.service.AccountPasswordEncoderService;
import com.aieducenter.aieducenteridentity.sso.config.SsoProperties;
import com.aieducenter.aieducenteridentity.sso.infrastructure.client.SsoClientCacheEntry;
import com.aieducenter.aieducenteridentity.sso.domain.session.SsoSession;
import com.aieducenter.aieducenteridentity.sso.domain.session.SsoSessionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;

import jakarta.servlet.http.Cookie;

/**
 * HTTP 黑盒集成测试基类。
 *
 * <p>背靠 Testcontainers（真 Postgres + Redis，见 {@link TestContainersConfig}），加载完整应用上下文，
 * 通过 {@link MockMvc} 走真实 HTTP → Controller → AppService → Redis 链路，不做 service 层 mock。
 *
 * <p>每个测试前 flush Redis + 重置消息捕获器 + 失效 SSO client 缓存，保证测试隔离。
 *
 * <p>SSO 是 identity 唯一用户会话（ADR-0004），故「demo 消费方常量（现由 {@link WireMockAppRegistryConfig}
 * stub 的 app-registry 提供，#30）+ 直接建号 + 建 SSO 会话/cookie」作为认人基础设施收在本基类，
 * account/sso 两组测试共用。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestContainersConfig.class, CapturingMessageSenderConfig.class, WireMockAppRegistryConfig.class,
    MutableClockConfig.class})
public abstract class IdentityIntegrationTestBase {

    /** demo 消费方（WireMock stub 的 app-registry 预置，#30 替 stub 适配器；常量不变供既有测试无感迁移）。 */
    protected static final String CLIENT_ID = "demo-client";
    protected static final String CLIENT_SECRET = "demo-secret-please-change";
    protected static final String REDIRECT_URI = "https://demo.localhost/auth/callback";
    /** demo 登出回跳落点（首页，与登录回调 REDIRECT_URI 分属两个白名单，ADR-0005）。 */
    protected static final String POST_LOGOUT_REDIRECT_URI = "https://demo.localhost/";

    /** app-registry 指向 WireMock（#30 sso-clients bootstrap；#46 api-keys bootstrap 同源）。 */
    @DynamicPropertySource
    static void appRegistryProperties(DynamicPropertyRegistry registry) {
        registry.add("identity.sso.app-registry.base-url", WireMockAppRegistryConfig.WIRE_MOCK::baseUrl);
        // #46：让 application.yml 的 cartisan.openapi.apikey-service-url（${APP_REGISTRY_BASE_URL:...}）也落到 WireMock，
        // 使 ApiKeyServiceUrlIntegrationTest 走真实的 URL 模板 + {apiKey} 占位符替换路径（占位符丢失会以 WireMock 未命中暴露）。
        registry.add("APP_REGISTRY_BASE_URL", WireMockAppRegistryConfig.WIRE_MOCK::baseUrl);
    }

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected StringRedisTemplate redisTemplate;

    @Autowired
    protected CapturingMessageSender capturingMessageSender;

    @Autowired
    protected AccountRepository accountRepository;

    @Autowired
    protected AccountPasswordEncoderService passwordEncoderService;

    @Autowired
    protected SsoSessionRepository sessionRepository;

    @Autowired
    protected SsoProperties ssoProperties;

    /** SSO client 本地缓存（Caffeine）；每测试前失效，保证 WireMock 调用计数与缓存行为可观测。 */
    @Autowired
    protected Cache<String, SsoClientCacheEntry> ssoClientCache;

    /** SsoClient 缓存 fresh/stale 判定时钟（测试可控）；每测试前复位，保证隔离（#32）。 */
    @Autowired
    protected MutableClock ssoMutableClock;

    @BeforeEach
    void resetTestState() {
        Objects.requireNonNull(redisTemplate.getConnectionFactory()).getConnection().flushDb();
        capturingMessageSender.reset();
        ssoClientCache.invalidateAll();
        ssoMutableClock.reset();
    }

    /** 直接建手机号账号（密码走真实加密），返回 userId。 */
    protected Long createPhoneAccount(String phone, String password) {
        Account account = Account.register(null, phone, passwordEncoderService.encodePassword(password));
        return accountRepository.save(account).getId();
    }

    /** 直接建邮箱账号（密码走真实加密），返回 userId。 */
    protected Long createEmailAccount(String email, String password) {
        Account account = Account.register(email, null, passwordEncoderService.encodePassword(password));
        return accountRepository.save(account).getId();
    }

    /** 建一个 SSO 会话（sessionId 可装入 cookie）。 */
    protected SsoSession createSsoSession(Long userId, String displayName) {
        return sessionRepository.create(userId, displayName);
    }

    /** 构造 SSO cookie。 */
    protected Cookie ssoCookie(String sessionId) {
        Cookie cookie = new Cookie(ssoProperties.getCookieName(), sessionId);
        cookie.setSecure(true);
        cookie.setHttpOnly(true);
        return cookie;
    }
}
