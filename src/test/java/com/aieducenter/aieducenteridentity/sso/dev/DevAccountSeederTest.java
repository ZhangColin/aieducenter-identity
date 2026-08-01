package com.aieducenter.aieducenteridentity.sso.dev;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

import com.aieducenter.aieducenteridentity.account.domain.repository.AccountRepository;
import com.aieducenter.aieducenteridentity.test.IdentityIntegrationTestBase;

/**
 * seeder 在上下文启动时跑（dev-login 开关开）；这里复用已起好的上下文验证账号已种。
 * 开关由 @TestPropertySource 打开 → 上下文启动时 seeder 注册并执行。
 */
@TestPropertySource(properties = "identity.sso.dev-login.enabled=true")
@DirtiesContext  // 本类用带 dev-login 的上下文，与默认上下文隔离
class DevAccountSeederTest extends IdentityIntegrationTestBase {

    @Autowired
    private AccountRepository accountRepository;

    @Test
    void seeded_demo_account_exists() {
        assertThat(accountRepository.findByEmail("demo@aieducenter.com"))
            .as("seeder 应在启动时种入 demo@aieducenter.com")
            .isPresent();
    }
}
