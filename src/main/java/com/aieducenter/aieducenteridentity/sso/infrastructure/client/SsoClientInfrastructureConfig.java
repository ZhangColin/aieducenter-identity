package com.aieducenter.aieducenteridentity.sso.infrastructure.client;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.aieducenter.aieducenteridentity.sso.domain.client.SsoClient;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

/**
 * SSO client 远程解析的基础设施装配（#30→#31）：Caffeine 缓存。
 *
 * <p>出站 HTTP + 服务间签名由框架 {@link com.cartisan.openapi.client.OpenApiClient}（自动配置 bean）承担——
 * {@link RemoteSsoClientRepositoryAdapter} 直接注入它，超时走 {@code cartisan.openapi.timeout.*}（#31），
 * 故本类不再装配 {@code RestClient}（#30 的 {@code appRegistryRestClient} bean + {@code SimpleClientHttpRequestFactory}
 * 已随切换删除）。</p>
 *
 * <p>{@link Cache} 30min TTL，由 {@link RemoteSsoClientRepositoryAdapter} 持有；测试注入同一 bean 做隔离失效。</p>
 *
 * @since 0.1.0
 */
@Configuration(proxyBeanMethods = false)
public class SsoClientInfrastructureConfig {

    /** 缓存 TTL（秒）——CONTEXT「本地缓存：Caffeine 30min」。 */
    private static final long CACHE_TTL_SECONDS = 1_800L;

    @Bean
    Cache<String, Optional<SsoClient>> ssoClientCache() {
        return Caffeine.newBuilder()
            .expireAfterWrite(CACHE_TTL_SECONDS, TimeUnit.SECONDS)
            .build();
    }
}
