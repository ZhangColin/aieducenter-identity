package com.aieducenter.aieducenteridentity.test;

import java.util.Objects;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.aieducenter.aieducenteridentity.account.domain.aggregate.Account;
import com.aieducenter.aieducenteridentity.account.domain.repository.AccountRepository;
import com.aieducenter.aieducenteridentity.account.domain.service.AccountPasswordEncoderService;
import com.aieducenter.aieducenteridentity.sso.config.SsoProperties;
import com.aieducenter.aieducenteridentity.sso.domain.session.SsoSession;
import com.aieducenter.aieducenteridentity.sso.domain.session.SsoSessionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.Cookie;

/**
 * HTTP 黑盒集成测试基类。
 *
 * <p>背靠 Testcontainers（真 Postgres + Redis，见 {@link TestContainersConfig}），加载完整应用上下文，
 * 通过 {@link MockMvc} 走真实 HTTP → Controller → AppService → Redis 链路，不做 service 层 mock。
 *
 * <p>每个测试前 flush Redis + 重置消息捕获器，保证测试隔离。
 *
 * <p>SSO 是 identity 唯一用户会话（ADR-0004），故「stub 消费方常量 + 直接建号 + 建 SSO 会话/cookie」
 * 作为认人基础设施收在本基类，account/sso 两组测试共用（注册 HTTP 入口随旧链路拆除，#18 在新链路重建，
 * 测试建号一律走 repository）。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestContainersConfig.class, CapturingMessageSenderConfig.class})
public abstract class IdentityIntegrationTestBase {

    /** stub 预置消费方（{@code SsoProperties} 默认值）。 */
    protected static final String CLIENT_ID = "demo-client";
    protected static final String CLIENT_SECRET = "demo-secret-please-change";
    protected static final String REDIRECT_URI = "https://demo.localhost/auth/callback";

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

    @BeforeEach
    void resetTestState() {
        Objects.requireNonNull(redisTemplate.getConnectionFactory()).getConnection().flushDb();
        capturingMessageSender.reset();
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
