package com.aieducenter.aieducenteridentity;

import org.junit.jupiter.api.Test;

import com.aieducenter.aieducenteridentity.test.IdentityIntegrationTestBase;

/**
 * 应用上下文冒烟测试：借 Testcontainers（真 PG + Redis）加载完整上下文。
 */
class AieducenterIdentityApplicationTest extends IdentityIntegrationTestBase {

    @Test
    void contextLoads() {
    }
}
