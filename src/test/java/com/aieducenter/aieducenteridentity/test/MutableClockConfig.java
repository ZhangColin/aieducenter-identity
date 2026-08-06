package com.aieducenter.aieducenteridentity.test;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.boot.test.context.TestConfiguration;

/**
 * 测试用可控时钟装配（#32 抖动降级测试基建）——{@code @Primary} 覆盖生产
 * {@code SsoClientInfrastructureConfig#ssoClock()} 的 {@code Clock.systemUTC()}，
 * 使 {@link com.aieducenter.aieducenteridentity.sso.infrastructure.client.RemoteSsoClientRepositoryAdapter}
 * 的 fresh/stale 判定用 {@link MutableClock}，测试可推进时间。
 */
@TestConfiguration(proxyBeanMethods = false)
public class MutableClockConfig {

    @Bean
    @Primary
    MutableClock ssoMutableClock() {
        return new MutableClock();
    }
}
