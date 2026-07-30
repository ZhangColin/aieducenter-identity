package com.aieducenter.aieducenteridentity.test;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Testcontainers 配置：单例 Postgres + Redis 容器。
 *
 * <p>容器以 {@code static final} 持有，JVM 级共享，跨测试类复用（只起一次）。
 * 通过 {@link ServiceConnection} 让 Spring Boot 自动把 datasource / Redis 连接指向容器，
 * 无需在 application-test.yml 里手写连接串。
 *
 * <p>Postgres 容器满足 {@code @SpringBootTest} 全量上下文对 datasource / JPA / Flyway 的要求
 * （verification 本身只用 Redis，但上下文启动需要 datasource）；Redis 容器承载验证码 / 限流真实存储。
 *
 * <p>Redis 用裸 {@code GenericContainer}（Testcontainers 官方无 Redis 专用容器类）。因 {@code GenericContainer}
 * 是通用容器，{@code @ServiceConnection} 无法凭容器类型推断服务，故显式声明 {@code name = "redis"}
 * 指向 Spring Boot 的 Redis ConnectionDetailsFactory。
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestContainersConfig {

    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    private static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    static {
        POSTGRES.start();
        REDIS.start();
    }

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgres() {
        return POSTGRES;
    }

    @Bean
    @ServiceConnection(name = "redis")
    GenericContainer<?> redis() {
        return REDIS;
    }
}
