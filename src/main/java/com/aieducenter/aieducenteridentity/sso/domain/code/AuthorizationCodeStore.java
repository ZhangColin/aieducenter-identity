package com.aieducenter.aieducenteridentity.sso.domain.code;

import java.util.Optional;

import com.cartisan.core.stereotype.Port;
import com.cartisan.core.stereotype.PortType;

/**
 * 授权码存储端口（南向，对一次性授权码的签发与消费能力的抽象）。
 *
 * <p>code 一次性 + 60s + 绑 client/redirect_uri（CONTEXT 安全集）：{@link #issue} 存入短命 code，
 * {@link #consume} 原子取删——同一 code 再消费返回 empty（防重放）。{@code /authorize} 与 {@code /api/sso/login}
 * 发 code、{@code /token} 换 code 走本端口。</p>
 *
 * @since 0.1.0
 */
@Port(PortType.CLIENT)
public interface AuthorizationCodeStore {

    /**
     * 签发授权码——生成不透明 code、存入载荷、设短命 TTL，返回 code。
     *
     * @param payload 绑定载荷（client/redirect_uri/user/nonce/scope）
     * @return 不透明授权码
     */
    String issue(IssuedAuthorizationCode payload);

    /**
     * 原子消费（GETDEL 语义）——返回并立即删除 code 对应载荷。
     *
     * <p>单次使用——同一 code 再 consume 返回 empty（已用/过期/未知）。</p>
     *
     * @param code 授权码
     * @return 命中返回载荷；不存在/已用/过期返回 empty
     */
    Optional<IssuedAuthorizationCode> consume(String code);
}
