package com.aieducenter.aieducenteridentity.sso.infrastructure.client;

import java.util.Optional;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.stereotype.Component;

import com.aieducenter.aieducenteridentity.sso.config.SsoProperties;
import com.aieducenter.aieducenteridentity.sso.domain.client.SsoClient;
import com.aieducenter.aieducenteridentity.sso.domain.client.SsoClientRepository;
import com.cartisan.core.stereotype.Adapter;
import com.cartisan.core.stereotype.PortType;
import com.cartisan.openapi.client.OpenApiClient;
import com.cartisan.web.response.ApiResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.github.benmanes.caffeine.cache.Cache;

/**
 * SsoClient 查询的远程适配器（#30 替 StubSsoClientRepositoryAdapter、#31 接服务间签名）——消费 app-registry bootstrap 端点。
 *
 * <p>{@code GET /api/app-registry/sso-clients/{clientId}}（{@code ApiResponse} 包装，取 {@code .data}）→
 * {@link SsoClient}。出站走框架 {@link OpenApiClient}——自动带 cartisan-openapi 服务间签名头
 * （app-registry bootstrap 标 {@code @RequireSignature}，#31）+ 跨服务 RequestContext 透传。</p>
 *
 * <p><b>本地缓存</b>（Caffeine 30min TTL）：命中省远程；{@code active=false}/{@code clientSecretHash=null}
 * 负面缓存；4xx/5xx/网络抖动<b>不缓存</b>（返 empty、下次重试），由上层转 {@code invalid_client}/
 * {@code unauthorized_client}（CONTEXT「本地缓存」）。含过期兜底的完整韧性留后续切片。</p>
 *
 * @since 0.1.0
 */
@Adapter(PortType.CLIENT)
@Component
public class RemoteSsoClientRepositoryAdapter implements SsoClientRepository {

    private static final Log logger = LogFactory.getLog(RemoteSsoClientRepositoryAdapter.class);

    /** app-registry bootstrap 端点路径模板（app-registry 契约，单一源）。 */
    static final String BOOTSTRAP_PATH_TEMPLATE = "/api/app-registry/sso-clients/{clientId}";

    /** 具体 clientId 拼出的 bootstrap 路径（测试/WireMock 复用，避免路径硬编码散落）。 */
    public static String bootstrapPath(String clientId) {
        return BOOTSTRAP_PATH_TEMPLATE.replace("{clientId}", clientId);
    }

    private static final TypeReference<ApiResponse<SsoClientInfo>> RESPONSE_TYPE = new TypeReference<>() {};

    private final OpenApiClient openApiClient;
    private final String baseUrl;
    private final Cache<String, Optional<SsoClient>> cache;

    public RemoteSsoClientRepositoryAdapter(OpenApiClient openApiClient, SsoProperties properties,
            Cache<String, Optional<SsoClient>> cache) {
        this.openApiClient = openApiClient;
        // 去尾斜杠，防 baseUrl 尾斜杠 + path 头斜杠 = 双斜杠（Spring 路径不匹配）。
        this.baseUrl = stripTrailingSlash(properties.getAppRegistry().getBaseUrl());
        this.cache = cache;
    }

    @Override
    public Optional<SsoClient> findByClientId(String clientId) {
        if (clientId == null || clientId.isBlank()) {
            return Optional.empty();
        }
        Optional<SsoClient> cached = cache.getIfPresent(clientId);
        if (cached != null) {
            return cached;
        }
        try {
            Optional<SsoClient> loaded = fetch(clientId);
            cache.put(clientId, loaded); // 命中结果入缓存（含 active=false/hash=null 负面缓存）
            return loaded;
        } catch (RuntimeException e) {
            // OpenApiClient：4xx/5xx 抛 OpenApiClientException、网络/超时/解析包成 RuntimeException；均不缓存，
            // 拒办（上层转 invalid_client / unauthorized_client），下次重试。连 throwable 一起记，便于排查被吞的异常。
            logger.warn("app-registry 拉取 SsoClient 失败，clientId=" + clientId, e);
            return Optional.empty();
        }
    }

    private Optional<SsoClient> fetch(String clientId) {
        ApiResponse<SsoClientInfo> body = openApiClient.get(baseUrl + bootstrapPath(clientId), RESPONSE_TYPE);
        if (body == null || body.data() == null) {
            return Optional.empty();
        }
        SsoClientInfo info = body.data();
        if (!info.active() || info.clientSecretHash() == null) {
            return Optional.empty();
        }
        return Optional.of(toSsoClient(info));
    }

    private static SsoClient toSsoClient(SsoClientInfo info) {
        // SsoClient 构造要求 redirectUris 非 null（否则 NPE），scopes/grants 容 null（自兜底 Set.of()），
        // 故仅对 redirectUris 做远程脏数据兜底——非防御不对称，是对齐端口契约的不变量。
        Set<String> redirectUris = info.redirectUris() == null ? Set.of() : Set.copyOf(info.redirectUris());
        return new SsoClient(
            info.clientId(),
            info.clientName(),
            info.clientSecretHash(),
            redirectUris,
            info.scopes(),
            info.grants(),
            info.active());
    }

    private static String stripTrailingSlash(String url) {
        return url == null || url.isBlank() ? "" : url.replaceAll("/+$", "");
    }
}
