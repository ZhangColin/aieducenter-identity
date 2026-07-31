package com.aieducenter.aieducenteridentity.sso.infrastructure.client;

import java.util.Optional;
import java.util.Set;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import com.aieducenter.aieducenteridentity.sso.config.SsoProperties;
import com.aieducenter.aieducenteridentity.sso.domain.client.SsoClient;
import com.aieducenter.aieducenteridentity.sso.domain.client.SsoClientRepository;
import com.cartisan.core.stereotype.Adapter;
import com.cartisan.core.stereotype.PortType;

/**
 * SsoClient 查询的 <b>stub 实现</b>（#15 测试/dev 便利）。
 *
 * <p>内存预置单一 demo 消费方（{@code identity.sso.stub-*}）；secret 用 BCrypt 自哈希（与
 * {@link BCryptClientSecretVerifierAdapter} 对齐）。<b>真 app-registry 接入在 #6</b>——届时本 stub 移除、
 * 换远程适配器 + Caffeine 缓存，secret 校验换 argon2。</p>
 *
 * @since 0.1.0
 */
@Adapter(PortType.CLIENT)
@Component
public class StubSsoClientRepositoryAdapter implements SsoClientRepository {

    private final SsoClient demoClient;

    public StubSsoClientRepositoryAdapter(SsoProperties properties) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String secretHash = encoder.encode(properties.getStubClientSecret());
        this.demoClient = new SsoClient(
            properties.getStubClientId(),
            "Demo 消费方",
            secretHash,
            Set.copyOf(properties.getStubRedirectUris()),
            Set.of("openid", "profile", "email", "phone"),
            Set.of("authorization_code", "refresh_token"),
            true);
    }

    @Override
    public Optional<SsoClient> findByClientId(String clientId) {
        if (clientId == null) {
            return Optional.empty();
        }
        return demoClient.clientId().equals(clientId) && demoClient.active()
            ? Optional.of(demoClient) : Optional.empty();
    }
}
