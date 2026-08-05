package com.aieducenter.aieducenteridentity.test;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

import java.util.List;
import java.util.Set;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;

import com.aieducenter.aieducenteridentity.sso.infrastructure.client.RemoteSsoClientRepositoryAdapter;
import com.aieducenter.aieducenteridentity.sso.infrastructure.client.SsoClientInfo;
import com.cartisan.web.response.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

/**
 * WireMock 测试基建（#30）：stub app-registry bootstrap 端点，集成测试消费方解析迁离 stub 适配器。
 *
 * <p>{@link WireMockServer} 以 {@code static final} 持有 + 动态端口，JVM 级共享（与 Testcontainers 同模式），
 * 启动即注册 demo-client stub（取自 {@link IdentityIntegrationTestBase} 的 {@code CLIENT_ID/CLIENT_SECRET/
 * REDIRECT_URI} 常量、argon2 真哈希、active=true），响应体用真实 {@link ApiResponse#ok} 序列化，保证 code
 * 形态与 app-registry 一致。</p>
 *
 * <p>{@code identity.sso.app-registry.base-url} 由测试基类的 {@code @DynamicPropertySource} 指向本服务器的 baseUrl。</p>
 *
 * <p>本切片（#6-a）不验签——WireMock 不校验签名头；#6-b 接服务间签名时再扩展。</p>
 */
@TestConfiguration(proxyBeanMethods = false)
public class WireMockAppRegistryConfig {

    public static final WireMockServer WIRE_MOCK =
        new WireMockServer(WireMockConfiguration.options().dynamicPort());

    static {
        WIRE_MOCK.start();
        registerDemoClientStub();
    }

    private static void registerDemoClientStub() {
        String hash = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8()
            .encode(IdentityIntegrationTestBase.CLIENT_SECRET);
        SsoClientInfo info = new SsoClientInfo(IdentityIntegrationTestBase.CLIENT_ID, 1L, "Demo 消费方", hash,
            List.of(IdentityIntegrationTestBase.REDIRECT_URI),
            Set.of("openid", "profile", "email", "phone"),
            Set.of("authorization_code", "refresh_token"), true);
        String body;
        try {
            body = new ObjectMapper().writeValueAsString(ApiResponse.ok(info));
        } catch (Exception e) {
            throw new RuntimeException("无法序列化 demo-client stub 响应体", e);
        }
        String bootstrapUrl = RemoteSsoClientRepositoryAdapter
            .bootstrapPath(IdentityIntegrationTestBase.CLIENT_ID);
        WIRE_MOCK.stubFor(get(urlEqualTo(bootstrapUrl)).willReturn(okJson(body)));
    }

    @Bean
    WireMockServer appRegistryWireMock() {
        return WIRE_MOCK;
    }
}
