package com.aieducenter.aieducenteridentity.sso.domain.token;

import java.util.Optional;

import com.cartisan.core.stereotype.Port;
import com.cartisan.core.stereotype.PortType;

/**
 * id_token 解码器端口（南向，对 JWT 解析能力的抽象，issue #45）。
 *
 * <p>仅解码取声明、<b>不验签</b>——用于 RP-initiated logout 的 {@code id_token_hint}：OIDC 规范里 hint 是
 * 「提示」非「凭据」，identity 当前不做完整验签 / 免确认（spec #39 明确留后续 issue），只取 {@code sub}
 * 写审计、并在 SSO cookie 缺失时作兜底会话定位。与 {@link IdTokenSigner}（签发）对称——签名细节不泄漏到应用层。</p>
 *
 * @since 0.1.0
 */
@Port(PortType.CLIENT)
public interface IdTokenDecoder {

    /**
     * 解码 id_token 取 {@code sub}（=userId），不验签。
     *
     * <p>入参空 / 解析失败 / 无 sub → 返回 {@link Optional#empty()}（OIDC：无效 hint 不阻断，按「无 hint」处理）。
     * 永不抛异常——登出主流程不受解码失败影响。</p>
     *
     * @param idTokenHint 紧凑序列化的 id_token（可空）
     * @return sub；解析不出则 empty
     */
    Optional<String> decodeSubject(String idTokenHint);
}
