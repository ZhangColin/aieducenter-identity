package com.aieducenter.aieducenteridentity.sso.application;

import org.springframework.stereotype.Service;

import com.aieducenter.aieducenteridentity.sso.application.dto.ClientInfoResponse;
import com.aieducenter.aieducenteridentity.sso.domain.client.SsoClient;
import com.aieducenter.aieducenteridentity.sso.domain.client.SsoClientValidationService;
import com.aieducenter.aieducenteridentity.sso.domain.error.OidcException;

/**
 * client-info 公开查询应用服务——登录页「登录到 XXX 应用」（issue #24）。
 *
 * <p>client 存活校验复用 {@link SsoClientValidationService#requireActiveClient}——与 {@code /authorize}
 * 同一真相源（安全关键逻辑只一份）：client_id 缺失/未注册/停用 → {@link OidcException}
 * （{@code unauthorized_client}，端口契约「未启用返回 empty」，故停用与不存在同一形态）。</p>
 *
 * @since 0.1.0
 */
@Service
public class SsoClientInfoAppService {

    private final SsoClientValidationService clientValidation;

    public SsoClientInfoAppService(SsoClientValidationService clientValidation) {
        this.clientValidation = clientValidation;
    }

    /**
     * 按 client_id 查消费方最小展示信息。
     *
     * @throws OidcException client_id 缺失/无效/停用（unauthorized_client）
     */
    public ClientInfoResponse clientInfo(String clientId) {
        SsoClient client = clientValidation.requireActiveClient(clientId);
        return new ClientInfoResponse(client.clientId(), client.clientName());
    }
}
