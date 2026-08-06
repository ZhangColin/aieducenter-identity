package com.aieducenter.aieducenteridentity.sso.infrastructure.client;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

/**
 * SSO client 远程解析的基础设施装配（#30→#31→#32）：Caffeine 缓存 + 时钟。
 *
 * <p>出站 HTTP + 服务间签名由框架 {@link com.cartisan.openapi.client.OpenApiClient}（自动配置 bean）承担——
 * {@link RemoteSsoClientRepositoryAdapter} 直接注入它，超时走 {@code cartisan.openapi.timeout.*}（#31），
 * 故本类不再装配 {@code RestClient}（#30 的 {@code appRegistryRestClient} bean + {@code SimpleClientHttpRequestFactory}
 * 已随切换删除）。</p>
 *
 * <p>{@link Cache} 缓存 {@link SsoClientCacheEntry}（值 + 写入时刻），{@code maximumSize} 上限、无时间淘汰——
 * last-known-good 尽量驻留，fresh/stale 判定由适配器据 {@link Clock} 算（#32 完整韧性）。测试以 {@code @Primary}
 * {@code MutableClock} 覆盖 {@link #ssoClock()}。</p>
 *
 * @since 0.1.0
 */
@Configuration(proxyBeanMethods = false)
public class SsoClientInfrastructureConfig {

    /** 缓存条目数上限——client 元数据变更稀疏、clientId 集合有界（平台消费方数量级），按大小淘汰足够。 */
    private static final long CACHE_MAXIMUM_SIZE = 10_000L;

    @Bean
    Cache<String, SsoClientCacheEntry> ssoClientCache() {
        return Caffeine.newBuilder()
            .maximumSize(CACHE_MAXIMUM_SIZE)
            .build();
    }

    /** SsoClient 缓存的 fresh/stale 判定时钟；测试以 {@code @Primary} {@code MutableClock} 覆盖。 */
    @Bean
    Clock ssoClock() {
        return Clock.systemUTC();
    }
}
