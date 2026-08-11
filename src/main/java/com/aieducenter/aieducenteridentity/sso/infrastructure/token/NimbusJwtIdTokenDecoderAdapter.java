package com.aieducenter.aieducenteridentity.sso.infrastructure.token;

import java.text.ParseException;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.aieducenter.aieducenteridentity.sso.domain.token.IdTokenDecoder;
import com.cartisan.core.stereotype.Adapter;
import com.cartisan.core.stereotype.PortType;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.JWTParser;

/**
 * id_token 解码器适配器——nimbus 实现 {@link IdTokenDecoder}（issue #45）。
 *
 * <p>用 {@link JWTParser#parse}（不验签）而非 {@code SignedJWT.parse}：对 plain/signed 结构都只取声明，
 * 不区分alg、不取密钥——hint 仅作「提示」。解析任一环节失败 → empty（见端口契约，不抛）。</p>
 *
 * @since 0.1.0
 */
@Adapter(PortType.CLIENT)
@Component
public class NimbusJwtIdTokenDecoderAdapter implements IdTokenDecoder {

    @Override
    public Optional<String> decodeSubject(String idTokenHint) {
        if (idTokenHint == null || idTokenHint.isBlank()) {
            return Optional.empty();
        }
        try {
            JWTClaimsSet claims = JWTParser.parse(idTokenHint).getJWTClaimsSet();
            String sub = claims.getSubject();
            return (sub != null && !sub.isBlank()) ? Optional.of(sub) : Optional.empty();
        } catch (ParseException ex) {
            return Optional.empty();
        }
    }
}
