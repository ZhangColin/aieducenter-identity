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
import com.cartisan.openapi.provider.ApiKeyInfo;
import com.cartisan.web.response.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

/**
 * WireMock 测试基建（#30）：stub app-registry bootstrap 端点，集成测试消费方解析迁离 stub 适配器。
 *
 * <p>{@link WireMockServer} 以 {@code static final} 持有 + 动态端口，JVM 级共享（与 Testcontainers 同模式），
 * 启动即注册两类 stub：</p>
 * <ul>
 *   <li><b>sso-clients</b>（#30）：demo-client bootstrap（取自 {@link IdentityIntegrationTestBase} 的
 *       {@code CLIENT_ID/CLIENT_SECRET/REDIRECT_URI} 常量、argon2 真哈希、active=true）；</li>
 *   <li><b>api-keys</b>（#58）：签名服务的可信调用方 stub（{@code GET /api/app-registry/api-keys/{apiKey}}
 *       → {@link ApiKeyInfo}），供 account / verification bc 的 {@code @RequireSignature} 端点集成测试
 *       （配合 {@link TestSignatureHelper}）消费，#58-#62 复用；</li>
 *   <li><b>api-keys（admin-console，#67）</b>：后台管理 BFF 调用方 stub——{@code appName="admin-console"}
 *       与 {@code identity.management.authorized-callers} 白名单同源，供 {@code @RequireManagementCaller}
 *       管理 gate 集成测试消费。</li>
 * </ul>
 * <p>响应体用真实 {@link ApiResponse#ok} 序列化，保证 code 形态与 app-registry 一致。</p>
 *
 * <p>{@code identity.sso.app-registry.base-url} 与 {@code APP_REGISTRY_BASE_URL}（apikey-service-url 引用）
 * 均由测试基类的 {@code @DynamicPropertySource} 指向本服务器的 baseUrl（#46）。</p>
 */
@TestConfiguration(proxyBeanMethods = false)
public class WireMockAppRegistryConfig {

    public static final WireMockServer WIRE_MOCK =
        new WireMockServer(WireMockConfiguration.options().dynamicPort());

    /** 签名服务测试调用方（api-keys stub 预置）；与 {@link TestSignatureHelper} 共用同一组凭据。 */
    public static final String SIGNED_CALLER_API_KEY = "signed-service-test-key";
    public static final String SIGNED_CALLER_APP_NAME = "签名服务测试调用方";
    public static final String SIGNED_CALLER_API_SECRET = "signed-service-test-secret-32bytes!";
    /** api-keys stub 路径（与 application.yml 的 apikey-service-url 模板一致）。 */
    public static final String API_KEYS_PATH = "/api/app-registry/api-keys/" + SIGNED_CALLER_API_KEY;

    /**
     * 后台管理 BFF 调用方（admin-console，#67）——{@code appName="admin-console"} 与
     * {@code identity.management.authorized-callers} 白名单同源，供管理 gate 集成测试消费。
     */
    public static final String ADMIN_CONSOLE_API_KEY = "admin-console-test-key";
    public static final String ADMIN_CONSOLE_APP_NAME = "admin-console";
    public static final String ADMIN_CONSOLE_API_SECRET = "admin-console-test-secret-32bytes!";
    public static final String ADMIN_CONSOLE_API_KEYS_PATH =
        "/api/app-registry/api-keys/" + ADMIN_CONSOLE_API_KEY;

    static {
        WIRE_MOCK.start();
        registerDemoClientStub();
        registerSignedCallerStub();
        registerAdminConsoleStub();
    }

    private static void registerDemoClientStub() {
        String hash = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8()
            .encode(IdentityIntegrationTestBase.CLIENT_SECRET);
        SsoClientInfo info = new SsoClientInfo(IdentityIntegrationTestBase.CLIENT_ID, 1L, "Demo 消费方", hash,
            List.of(IdentityIntegrationTestBase.REDIRECT_URI),
            List.of(IdentityIntegrationTestBase.POST_LOGOUT_REDIRECT_URI),
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

    /**
     * 注册签名服务可信调用方 stub（#58）：{@code GET /api/app-registry/api-keys/{apiKey}} →
     * {@link ApiKeyInfo}，形态与 app-registry 真实端点一致。框架 {@code RemoteApiKeyProvider} 解析入站
     * {@code X-Api-Key} 时命中本 stub，Caffeine 缓存后供 {@code SignatureVerificationFilter} 验签。
     */
    private static void registerSignedCallerStub() {
        registerApiKeyStub(API_KEYS_PATH, SIGNED_CALLER_API_KEY, SIGNED_CALLER_APP_NAME,
            SIGNED_CALLER_API_SECRET);
    }

    /**
     * 注册后台管理 BFF 调用方 stub（#67）：与 {@link #registerSignedCallerStub()} 同形态，仅 appName
     * 取 {@code "admin-console"}（与 {@code identity.management.authorized-callers} 白名单同源）。
     * 管理 gate 集成测试以 {@link #ADMIN_CONSOLE_API_KEY} / {@link #ADMIN_CONSOLE_API_SECRET} 打签名，
     * 框架解析后 {@code RequestContext.getCallerAppName()} 即 "admin-console"。
     */
    private static void registerAdminConsoleStub() {
        registerApiKeyStub(ADMIN_CONSOLE_API_KEYS_PATH, ADMIN_CONSOLE_API_KEY, ADMIN_CONSOLE_APP_NAME,
            ADMIN_CONSOLE_API_SECRET);
    }

    /**
     * 注册一组 api-keys stub（{@code GET /api/app-registry/api-keys/{apiKey}} → {@link ApiKeyInfo}），
     * 供 {@link #registerSignedCallerStub()} / {@link #registerAdminConsoleStub()} 复用——响应体用真实
     * {@link ApiResponse#ok} 序列化，保证 code 形态与 app-registry 一致。
     */
    private static void registerApiKeyStub(String path, String apiKey, String appName, String apiSecret) {
        String body;
        try {
            body = new ObjectMapper().writeValueAsString(
                ApiResponse.ok(new ApiKeyInfo(apiKey, appName, apiSecret)));
        } catch (Exception e) {
            throw new RuntimeException("无法序列化 api-keys stub 响应体：" + appName, e);
        }
        WIRE_MOCK.stubFor(get(urlEqualTo(path)).willReturn(okJson(body)));
    }

    @Bean
    WireMockServer appRegistryWireMock() {
        return WIRE_MOCK;
    }
}
