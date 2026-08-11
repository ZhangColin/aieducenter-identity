package com.aieducenter.aieducenteridentity.openapi;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.aieducenter.aieducenteridentity.test.IdentityIntegrationTestBase;
import com.cartisan.openapi.provider.ApiKeyInfo;
import com.cartisan.openapi.provider.ApiKeyProvider;
import com.cartisan.web.response.ApiResponse;
import com.github.tomakehurst.wiremock.WireMockServer;

/**
 * {@code cartisan.openapi.apikey-service-url} 配置守护集成测试（issue #46）：证明 application.yml 的
 * {@code ${APP_REGISTRY_BASE_URL:...}/api/app-registry/api-keys/{apiKey}} 模板可被框架 {@link ApiKeyProvider}
 * 正确消费——{@code {apiKey}} 占位符按调用方 key 替换、base-url 解析到 app-registry。
 *
 * <p>identity 当前没有任何 {@code @RequireSignature} 入站端点（入站鉴权走 SSO cookie），故
 * {@code RemoteApiKeyProvider} 不在真实请求路径上（{@code SignatureVerificationFilter} 无 {@code X-Api-Key} 即跳过）。
 * 本测试直接以 {@link ApiKeyProvider} 公开接口为 seam，证明「配置 → URL 模板 → 占位符替换 → 远端解析」全链路通，
 * 而非伪造 identity 不具备的签名端点。</p>
 *
 * <p>刻意注入 {@code APP_REGISTRY_BASE_URL}（见 {@link IdentityIntegrationTestBase}）而非覆盖完整 URL：
 * 让测试走真实的 application.yml 配置模式。{@code {apiKey}} 占位符丢失类回归（如 #46 之前的 stale URL）
 * 会以「provider 返 null + WireMock 未被命中」双重断言失败暴露（参照 payment#2）。</p>
 */
class ApiKeyServiceUrlIntegrationTest extends IdentityIntegrationTestBase {

    /** WireMock 预置的调用方凭据；appName/secret 是断言回显的独立真值（不从被测代码推导，防 tautological）。 */
    private static final String CALLER_API_KEY = "inbound-sign-test-key";
    private static final String CALLER_APP_NAME = "入站验签测试调用方";
    private static final String CALLER_API_SECRET = "inbound-sign-test-secret-32bytes!";
    private static final String BOOTSTRAP_PATH = "/api/app-registry/api-keys/" + CALLER_API_KEY;

    @Autowired
    private ApiKeyProvider apiKeyProvider;

    @Autowired
    private WireMockServer appRegistryWireMock;

    @Test
    void given_apikey_service_url_template_when_lookup_api_key_then_placeholder_replaced() throws Exception {
        // stub app-registry bootstrap（GET /api/app-registry/api-keys/{apiKey}）返 ApiKeyInfo，形态与真实端点一致。
        String body = objectMapper.writeValueAsString(
            ApiResponse.ok(new ApiKeyInfo(CALLER_API_KEY, CALLER_APP_NAME, CALLER_API_SECRET)));
        appRegistryWireMock.stubFor(get(urlEqualTo(BOOTSTRAP_PATH)).willReturn(okJson(body)));

        // 以框架自动装配的 ApiKeyProvider 为 seam——它读 application.yml 的 apikey-service-url。
        ApiKeyInfo info = apiKeyProvider.getByApiKey(CALLER_API_KEY);

        // GREEN#1：{apiKey} 占位符替换 + base-url 解析都正确 → 远端凭据被解析回来。
        assertThat(info).isNotNull();
        assertThat(info.apiKey()).isEqualTo(CALLER_API_KEY);
        assertThat(info.appName()).isEqualTo(CALLER_APP_NAME);
        assertThat(info.apiSecret()).isEqualTo(CALLER_API_SECRET);

        // GREEN#2：占位符替换的独立佐证——WireMock 在替换后的精确路径上被命中。
        // 若 application.yml 丢字面 {apiKey}（#46 之前的 stale URL），provider 会打到错的 URL、上面 info 为 null——
        // 回归以「WireMock 未命中」暴露。
        appRegistryWireMock.verify(getRequestedFor(urlEqualTo(BOOTSTRAP_PATH)));
    }
}
