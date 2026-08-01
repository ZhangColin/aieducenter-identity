package com.aieducenter.demobff.sso;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClient.Builder;

import com.aieducenter.demobff.config.SsoProperties;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.JWTParser;

/**
 * OIDC 客户端核心（issue #16）：构造 /authorize URL、用 code 换 token（服务端带 client_secret）、解 id_token。
 */
@Component
public class OidcClient {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final SsoProperties props;
    private final RestClient tokenClient;

    public OidcClient(SsoProperties props, Builder restClientBuilder) {
        this.props = props;
        this.tokenClient = restClientBuilder.baseUrl(props.getIssuer()).build();
    }

    /** 构造 /authorize 跳转 URL（带 state/nonce 防 CSRF/重放）。 */
    public String authorizeUrl(String state, String nonce) {
        return props.getIssuer() + "/authorize"
            + "?client_id=" + enc(props.getClientId())
            + "&redirect_uri=" + enc(props.getRedirectUri())
            + "&response_type=code"
            + "&scope=" + enc(props.getScope())
            + "&state=" + enc(state)
            + "&nonce=" + enc(nonce);
    }

    /** 用 code + client_secret 服务端换 token（grant_type=authorization_code）。 */
    public TokenResponse exchangeCode(String code) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("redirect_uri", props.getRedirectUri());
        form.add("client_id", props.getClientId());
        form.add("client_secret", props.getClientSecret());
        return tokenClient.post().uri("/token")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(form)
            .retrieve().body(TokenResponse.class);
    }

    /** id_token 的用户声明（不验签：经可信机机通道取得；#17 引入 /jwks 后补验签）。 */
    public IdTokenClaims decodeIdToken(String idToken) {
        try {
            JWTClaimsSet c = JWTParser.parse(idToken).getJWTClaimsSet();
            return new IdTokenClaims(
                c.getSubject(),
                c.getStringClaim("email"),
                c.getStringClaim("nickname"),
                c.getStringClaim("picture"));
        } catch (Exception e) {
            throw new IllegalStateException("id_token 解析失败", e);
        }
    }

    /** 不透明随机串（业务 sessionId / state / nonce 共用）。 */
    public static String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String enc(String v) {
        return URLEncoder.encode(v, StandardCharsets.UTF_8);
    }

    /** id_token 解出的用户信息。 */
    public record IdTokenClaims(String subject, String email, String nickname, String picture) {
    }
}
