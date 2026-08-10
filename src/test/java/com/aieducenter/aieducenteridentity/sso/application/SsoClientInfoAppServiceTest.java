package com.aieducenter.aieducenteridentity.sso.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.aieducenter.aieducenteridentity.sso.application.dto.ClientInfoResponse;
import com.aieducenter.aieducenteridentity.sso.domain.client.SsoClient;
import com.aieducenter.aieducenteridentity.sso.domain.client.SsoClientRepository;
import com.aieducenter.aieducenteridentity.sso.domain.client.SsoClientValidationService;
import com.aieducenter.aieducenteridentity.sso.domain.error.OidcException;
import com.aieducenter.aieducenteridentity.sso.domain.error.SsoError;

class SsoClientInfoAppServiceTest {

    private static final String CLIENT_ID = "demo-client";

    private final SsoClientRepository clientRepository = mock(SsoClientRepository.class);
    private final SsoClientInfoAppService service =
        new SsoClientInfoAppService(new SsoClientValidationService(clientRepository));

    @Test
    void given_active_client_when_client_info_then_minimal_fields_mapped() {
        SsoClient client = new SsoClient(CLIENT_ID, "Demo 消费方", "secret-hash",
            Set.of("https://demo.localhost/auth/callback"), Set.of(), Set.of("openid"), Set.of("authorization_code"), true);
        when(clientRepository.findByClientId(CLIENT_ID)).thenReturn(Optional.of(client));

        ClientInfoResponse response = service.clientInfo(CLIENT_ID);

        // 只映射展示字段——secret/redirectUris/scopes 不进响应（record 结构上就拿不到）
        assertThat(response).isEqualTo(new ClientInfoResponse(CLIENT_ID, "Demo 消费方"));
    }

    /**
     * 停用与不存在同一形态：端口契约「未启用返回 empty」（{@code SsoClientRepository.findByClientId}），
     * HTTP 层 stub 无法表达停用，故在本 seam 覆盖（issue #24 AC）。
     */
    @Test
    void given_inactive_or_unknown_client_when_client_info_then_unauthorized_client() {
        when(clientRepository.findByClientId("disabled-client")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.clientInfo("disabled-client"))
            .isInstanceOf(OidcException.class)
            .extracting(ex -> ((OidcException) ex).error())
            .isEqualTo(SsoError.UNAUTHORIZED_CLIENT);
    }

    @Test
    void given_blank_client_id_when_client_info_then_unauthorized_client() {
        assertThatThrownBy(() -> service.clientInfo(" "))
            .isInstanceOf(OidcException.class)
            .extracting(ex -> ((OidcException) ex).error())
            .isEqualTo(SsoError.UNAUTHORIZED_CLIENT);
    }
}
