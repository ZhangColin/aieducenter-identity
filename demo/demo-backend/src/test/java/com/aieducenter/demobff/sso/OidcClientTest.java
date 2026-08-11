package com.aieducenter.demobff.sso;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.aieducenter.demobff.config.SsoProperties;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;

class OidcClientTest {

    private SsoProperties props(String issuer) {
        SsoProperties p = new SsoProperties();
        p.setIssuer(issuer);
        p.setClientId("demo-client");
        p.setClientSecret("demo-secret-please-change");
        p.setRedirectUri("http://demo.localhost:3000/auth/callback");
        p.setScope("openid profile");
        return p;
    }

    @Test
    void authorizeUrl_contains_required_params() {
        OidcClient client = new OidcClient(props("http://idp"), RestClient.builder());
        String url = client.authorizeUrl("st", "nc");
        assertThat(url).startsWith("http://idp/authorize?");
        assertThat(url).contains("client_id=demo-client");
        assertThat(url).contains("response_type=code");
        assertThat(url).contains("state=st").contains("nonce=nc");
    }

    @Test
    void logoutUrl_contains_required_params() {
        // issue #38：登出走 identity RP-Initiated Logout——构造 GET /logout URL：
        // client_id（identity 凭它解析 post_logout_redirect_uri 白名单）+ post_logout_redirect_uri + state。
        // id_token_hint 第 3 参为 null（无 BFF session / session 无 id_token）时不带（issue #40）。
        OidcClient client = new OidcClient(props("http://idp"), RestClient.builder());
        String url = client.logoutUrl("http://demo.localhost:3000/", "st", null);
        assertThat(url).startsWith("http://idp/logout?");
        assertThat(url).contains("client_id=demo-client");
        assertThat(url).contains("post_logout_redirect_uri="
            + URLEncoder.encode("http://demo.localhost:3000/", StandardCharsets.UTF_8));
        assertThat(url).contains("state=st");
        assertThat(url).doesNotContain("id_token_hint");
    }

    @Test
    void logoutUrl_appends_id_token_hint_when_present() {
        // issue #40：带上当前 BFF session 持有的 id_token 作 id_token_hint——identity 凭 hint 的 sub 在
        // SSO cookie 丢失时兜底定位会话（#45）并记审计。值为原始 JWT，需 URL 编码。
        OidcClient client = new OidcClient(props("http://idp"), RestClient.builder());
        String idToken = "eyJhbGci.eyJzdWIiOiJ1MSJ9.sig";
        String url = client.logoutUrl("http://demo.localhost:3000/", "st", idToken);
        assertThat(url).contains("id_token_hint="
            + URLEncoder.encode(idToken, StandardCharsets.UTF_8));
    }

    @Test
    void logoutUrl_omits_id_token_hint_when_blank() {
        // issue #40：hint 可选——空串/blank 视同无 id_token，不带 id_token_hint（登出主流程靠 SSO cookie）。
        OidcClient client = new OidcClient(props("http://idp"), RestClient.builder());
        String url = client.logoutUrl("http://demo.localhost:3000/", "st", "  ");
        assertThat(url).doesNotContain("id_token_hint");
    }

    @Test
    void exchangeCode_parses_token_response() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(once(), requestTo("http://idp/token"))
            .andRespond(withSuccess(
                "{\"access_token\":\"a\",\"token_type\":\"Bearer\",\"expires_in\":900,"
                + "\"refresh_token\":\"r\",\"id_token\":\"\"}",
                MediaType.APPLICATION_JSON));

        TokenResponse t = new OidcClient(props("http://idp"), builder).exchangeCode("code1");
        server.verify();
        assertThat(t.accessToken()).isEqualTo("a");
        assertThat(t.refreshToken()).isEqualTo("r");
    }

    @Test
    void exchangeCode_surfaces_http_status_and_body_on_token_error() {
        // issue #35：identity /token 的具体拒绝原因（{error, error_description}）不能再被吞成笼统失败。
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        String oidcErrorBody = "{\"error\":\"invalid_client\",\"error_description\":\"client 认证失败\"}";
        server.expect(once(), requestTo("http://idp/token"))
            .andRespond(withStatus(HttpStatus.UNAUTHORIZED).body(oidcErrorBody)
                .contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> new OidcClient(props("http://idp"), builder).exchangeCode("code1"))
            .isInstanceOf(TokenExchangeException.class)
            .satisfies(ex -> {
                TokenExchangeException tee = (TokenExchangeException) ex;
                assertThat(tee.getHttpStatus()).isEqualTo(401);
                assertThat(tee.getResponseBody()).contains("invalid_client");
            });
        server.verify();
    }

    @Test
    void decodeIdToken_reads_claims() throws Exception {
        String idToken = new PlainJWT(new JWTClaimsSet.Builder()
            .subject("u1").claim("email", "a@b.c").claim("nickname", "A").claim("picture", "p")
            .build()).serialize();
        OidcClient client = new OidcClient(props("http://idp"), RestClient.builder());
        TokenResponse t = new TokenResponse("a", "Bearer", 900L, "r", idToken);
        var claims = client.decodeIdToken(t.idToken());
        assertThat(claims.subject()).isEqualTo("u1");
        assertThat(claims.email()).isEqualTo("a@b.c");
        assertThat(claims.nickname()).isEqualTo("A");
    }

    @Test
    void randomToken_is_unique() {
        OidcClient client = new OidcClient(props("http://idp"), RestClient.builder());
        assertThat(OidcClient.randomToken()).isNotEqualTo(OidcClient.randomToken());
    }
}
