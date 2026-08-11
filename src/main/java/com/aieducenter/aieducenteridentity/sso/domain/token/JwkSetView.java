package com.aieducenter.aieducenteridentity.sso.domain.token;

import java.util.List;

/**
 * JWK Set 视图——{@code /jwks} 返回的公钥集（issue #17）。
 *
 * <p>序列化即 {@code {"keys":[...]}} 形态的合规 JWK Set。</p>
 *
 * @param keys 公钥列表
 *
 * @since 0.1.0
 */
public record JwkSetView(
    List<JwkView> keys
) {
}
