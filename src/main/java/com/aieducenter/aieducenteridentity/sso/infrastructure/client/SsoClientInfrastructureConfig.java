package com.aieducenter.aieducenteridentity.sso.infrastructure.client;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import com.aieducenter.aieducenteridentity.sso.config.SsoProperties;
import com.aieducenter.aieducenteridentity.sso.domain.client.SsoClient;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

/**
 * SSO client 远程解析的基础设施装配（#30）：app-registry bootstrap 的 {@link RestClient} + Caffeine 缓存。
 *
 * <p>{@link RestClient} 按 {@code identity.sso.app-registry.base-url} + 超时建（#6-b 补签名拦截器时在此扩展）。
 * {@link Cache} 30min TTL，由 {@link RemoteSsoClientRepositoryAdapter} 持有；测试注入同一 bean 做隔离失效。</p>
 *
 * @since 0.1.0
 */
@Configuration(proxyBeanMethods = false)
public class SsoClientInfrastructureConfig {

    /** 缓存 TTL（秒）——CONTEXT「本地缓存：Caffeine 30min」。 */
    private static final long CACHE_TTL_SECONDS = 1_800L;

    @Bean
    RestClient appRegistryRestClient(RestClient.Builder builder, SsoProperties properties) {
        SsoProperties.AppRegistry cfg = properties.getAppRegistry();
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) TimeUnit.SECONDS.toMillis(cfg.getConnectTimeoutSeconds()));
        factory.setReadTimeout((int) TimeUnit.SECONDS.toMillis(cfg.getReadTimeoutSeconds()));
        return builder.baseUrl(cfg.getBaseUrl()).requestFactory(factory).build();
    }

    @Bean
    Cache<String, Optional<SsoClient>> ssoClientCache() {
        return Caffeine.newBuilder()
            .expireAfterWrite(CACHE_TTL_SECONDS, TimeUnit.SECONDS)
            .build();
    }
}
