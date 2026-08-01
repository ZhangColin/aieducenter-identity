package com.aieducenter.aieducenteridentity.sso.dev;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.aieducenter.aieducenteridentity.account.domain.aggregate.Account;
import com.aieducenter.aieducenteridentity.account.domain.aggregate.Profile;
import com.aieducenter.aieducenteridentity.account.domain.repository.AccountRepository;
import com.aieducenter.aieducenteridentity.account.domain.repository.ProfileRepository;
import com.aieducenter.aieducenteridentity.account.domain.service.AccountPasswordEncoderService;
import com.aieducenter.aieducenteridentity.sso.config.SsoProperties;

/**
 * dev 测试账号 seeder（issue #16）。
 *
 * <p>{@code identity.sso.dev-login.enabled=true} 时启动：若 {@code demo@aieducenter.com} 不存在则建账号 + Profile。
 * 幂等（已存在即跳过）。dev-login 端点登这个账号。仅 dev/local 联调用，prod 不开此开关。</p>
 */
@Component
@ConditionalOnProperty(prefix = "identity.sso.dev-login", name = "enabled", havingValue = "true")
public class DevAccountSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevAccountSeeder.class);

    private final AccountRepository accountRepository;
    private final ProfileRepository profileRepository;
    private final AccountPasswordEncoderService passwordEncoderService;
    private final SsoProperties properties;

    public DevAccountSeeder(AccountRepository accountRepository, ProfileRepository profileRepository,
            AccountPasswordEncoderService passwordEncoderService, SsoProperties properties) {
        this.accountRepository = accountRepository;
        this.profileRepository = profileRepository;
        this.passwordEncoderService = passwordEncoderService;
        this.properties = properties;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        SsoProperties.DevLogin cfg = properties.getDevLogin();
        if (accountRepository.existsByEmail(cfg.getAccountEmail())) {
            return;
        }
        String encoded = passwordEncoderService.encodePassword(cfg.getAccountPassword());
        Account saved = accountRepository.save(
            Account.register(cfg.getAccountEmail(), null, encoded));
        profileRepository.save(Profile.create(saved.getId(), cfg.getAccountNickname(), null));
        log.info("dev 测试账号已种：{}（userId={}）", cfg.getAccountEmail(), saved.getId());
    }
}
