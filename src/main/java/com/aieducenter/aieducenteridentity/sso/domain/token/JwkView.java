package com.aieducenter.aieducenteridentity.sso.domain.token;

/**
 * 单个签名公钥的 JWK 视图（{@code /jwks} 返回，issue #17）。
 *
 * <p>字段名即 JWK 标准字段（{@code kty/use/alg/kid/n/e}）——序列化即合规 JWK。{@code n}/{@code e}
 * 为 base64url 编码的 RSA 模数 / 指数，对接方据此重构公钥本地验签。</p>
 *
 * @param kty 密钥类型（RSA）
 * @param use 用途（sig）
 * @param alg 算法（RS256）
 * @param kid 密钥 ID（与 JWT 头 kid 对应）
 * @param n   RSA 模数（base64url）
 * @param e   RSA 公钥指数（base64url，通常 {@code AQAB}）
 *
 * @since 0.1.0
 */
public record JwkView(
    String kty,
    String use,
    String alg,
    String kid,
    String n,
    String e
) {
}
