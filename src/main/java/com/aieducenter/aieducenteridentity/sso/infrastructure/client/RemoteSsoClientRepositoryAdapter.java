package com.aieducenter.aieducenteridentity.sso.infrastructure.client;

import java.time.Clock;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.aieducenter.aieducenteridentity.sso.config.SsoProperties;
import com.aieducenter.aieducenteridentity.sso.domain.client.SsoClient;
import com.aieducenter.aieducenteridentity.sso.domain.client.SsoClientRepository;
import com.cartisan.core.stereotype.Adapter;
import com.cartisan.core.stereotype.PortType;
import com.cartisan.openapi.client.OpenApiClient;
import com.cartisan.openapi.client.OpenApiClientException;
import com.cartisan.web.response.ApiResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.github.benmanes.caffeine.cache.Cache;

/**
 * SsoClient 查询的远程适配器（#30 替 StubSsoClientRepositoryAdapter、#31 接服务间签名、#32 完整韧性）——
 * 消费 app-registry bootstrap 端点。
 *
 * <p>{@code GET /api/app-registry/sso-clients/{clientId}}（{@code ApiResponse} 包装，取 {@code .data}）→
 * {@link SsoClient}。出站走框架 {@link OpenApiClient}——自动带 cartisan-openapi 服务间签名头
 * （app-registry bootstrap 标 {@code @RequireSignature}，#31）+ 跨服务 RequestContext 透传。</p>
 *
 * <p><b>本地缓存（Caffeine，完整韧性）</b>：按 clientId 缓存 {@link SsoClientCacheEntry}（值 + 写入时刻），
 * {@code maximumSize} 上限、无时间淘汰（last-known-good 尽量驻留以兜底）。
 * <ul>
 *   <li><b>fresh 窗口</b>（{@link #FRESH_TTL_MILLIS}，30min）：命中缓存不打远程——{@code /authorize} 高频查询不轰 app-registry。</li>
 *   <li><b>负面缓存</b>：{@code active=false} / {@code clientSecretHash=null} / {@code 404「查不到」} 也缓存
 *       （{@code Optional.empty()}），禁用/不存在的 client 不反复打 app-registry。</li>
 *   <li><b>抖动降级</b>：app-registry 抖动（5xx/连接失败/超时/其它非 404 错）时——<b>有过期缓存（哪怕 negative）就兜底</b>
 *       （值即最近一次成功解析，不返脏数据）、<b>无缓存就拒办</b>（返 empty，上层转 {@code unauthorized_client}/
 *       {@code invalid_client}），不抛 500。404 不视为抖动（是「查不到」的稳定结论，走负面缓存）。</li>
 * </ul>
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

    /**
     * fresh 窗口（毫秒）——写入后这么久内命中缓存不打远程（CONTEXT「本地缓存：Caffeine 30min」）。
     * 超过即 stale：下次访问会打远程刷新；刷新失败（抖动）则用过期缓存兜底。
     */
    public static final long FRESH_TTL_MILLIS = TimeUnit.MINUTES.toMillis(30);

    private static final TypeReference<ApiResponse<SsoClientInfo>> RESPONSE_TYPE = new TypeReference<>() {};

    private final OpenApiClient openApiClient;
    private final String baseUrl;
    private final Cache<String, SsoClientCacheEntry> cache;
    private final Clock clock;

    public RemoteSsoClientRepositoryAdapter(OpenApiClient openApiClient, SsoProperties properties,
            Cache<String, SsoClientCacheEntry> cache, Clock clock) {
        this.openApiClient = openApiClient;
        // 去尾斜杠，防 baseUrl 尾斜杠 + path 头斜杠 = 双斜杠（Spring 路径不匹配）。
        this.baseUrl = stripTrailingSlash(properties.getAppRegistry().getBaseUrl());
        this.cache = cache;
        this.clock = clock;
    }

    @Override
    public Optional<SsoClient> findByClientId(String clientId) {
        if (clientId == null || clientId.isBlank()) {
            return Optional.empty();
        }
        SsoClientCacheEntry resident = cache.getIfPresent(clientId);
        if (resident != null && isFresh(resident)) {
            return resident.value(); // fresh → 命中缓存、不打远程
        }
        try {
            Optional<SsoClient> loaded = fetch(clientId); // 成功解析（含 active=false/hash=null 负面）
            put(clientId, loaded);
            return loaded;
        } catch (OpenApiClientException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND.value()) {
                put(clientId, Optional.empty()); // 404「查不到」→ 负面缓存
                return Optional.empty();
            }
            return serveStaleOrReject(clientId, resident, e); // 5xx/其它 → 抖动降级
        } catch (RuntimeException e) {
            // OpenApiClient 把网络/超时（非 OpenApiClientException）包成 RuntimeException → 抖动降级。
            return serveStaleOrReject(clientId, resident, e);
        }
    }

    /**
     * 抖动降级：有过期缓存（哪怕 negative）就兜底（值即最近一次成功解析、不返脏数据）、无缓存就拒办（empty）。
     * 不抛——上层（SsoClientValidationService）据 empty 转 unauthorized_client/invalid_client，不抛 500。
     */
    private Optional<SsoClient> serveStaleOrReject(String clientId, SsoClientCacheEntry resident, RuntimeException cause) {
        logger.warn("app-registry 拉取 SsoClient 失败，clientId=" + clientId
            + (resident != null ? "，用过期缓存兜底" : "，无缓存拒办"), cause);
        if (resident != null) {
            return resident.value();
        }
        return Optional.empty();
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

    private void put(String clientId, Optional<SsoClient> value) {
        cache.put(clientId, new SsoClientCacheEntry(value, clock.millis()));
    }

    private boolean isFresh(SsoClientCacheEntry entry) {
        return clock.millis() - entry.writtenAtMillis() < FRESH_TTL_MILLIS;
    }

    private static SsoClient toSsoClient(SsoClientInfo info) {
        // SsoClient 构造要求 redirectUris 非 null（否则 NPE），scopes/grants/postLogoutRedirectUris 容 null
        // （自兜底 Set.of()），故仅对 redirectUris 做远程脏数据兜底——非防御不对称，是对齐端口契约的不变量。
        // postLogoutRedirectUris 容 null：app-registry 未下发该字段时（#18 未落地）为空集 = 不跳转（ADR-0005 兜底）。
        Set<String> redirectUris = info.redirectUris() == null ? Set.of() : Set.copyOf(info.redirectUris());
        Set<String> postLogoutRedirectUris =
            info.postLogoutRedirectUris() == null ? Set.of() : Set.copyOf(info.postLogoutRedirectUris());
        return new SsoClient(
            info.clientId(),
            info.clientName(),
            info.clientSecretHash(),
            redirectUris,
            postLogoutRedirectUris,
            info.scopes(),
            info.grants(),
            info.active());
    }

    private static String stripTrailingSlash(String url) {
        return url == null || url.isBlank() ? "" : url.replaceAll("/+$", "");
    }
}
