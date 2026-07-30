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

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * HTTP 黑盒集成测试基类。
 *
 * <p>背靠 Testcontainers（真 Postgres + Redis，见 {@link TestContainersConfig}），加载完整应用上下文，
 * 通过 {@link MockMvc} 走真实 HTTP → Controller → AppService → Redis 链路，不做 service 层 mock。
 *
 * <p>每个测试前 flush Redis + 重置消息捕获器，保证测试隔离。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestContainersConfig.class, CapturingMessageSenderConfig.class})
public abstract class IdentityIntegrationTestBase {

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected StringRedisTemplate redisTemplate;

    @Autowired
    protected CapturingMessageSender capturingMessageSender;

    @BeforeEach
    void resetTestState() {
        Objects.requireNonNull(redisTemplate.getConnectionFactory()).getConnection().flushDb();
        capturingMessageSender.reset();
    }
}
