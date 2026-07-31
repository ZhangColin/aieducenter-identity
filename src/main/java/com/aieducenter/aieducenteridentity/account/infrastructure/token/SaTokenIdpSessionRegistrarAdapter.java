package com.aieducenter.aieducenteridentity.account.infrastructure.token;

import java.time.Instant;

import org.springframework.stereotype.Component;

import com.aieducenter.aieducenteridentity.account.domain.token.IdpSessionRegistrar;
import com.cartisan.core.stereotype.Adapter;
import com.cartisan.core.stereotype.PortType;

import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.stp.parameter.SaLoginParameter;

/**
 * IdP 会话注册器适配器——用 Sa-Token 实现 {@link IdpSessionRegistrar}（ADR-0003 / issue #11）。
 *
 * <p><b>核心</b>：access JWT 同时作 Sa-Token 会话 token 的值——
 * {@code StpUtil.login(userId, SaLoginParameter.setToken(accessJwt).setTimeout(ttl))}。
 * Sa-Token 把 JWT 当不透明 key 存（{@code jwt→userId}，不验签）；签名给外部对接方 / 未来 {@code /jwks}。</p>
 *
 * <p><b>bug#1 保留</b>：登录后 {@code getSession().set("userName", displayName)}，SecurityFilter 后续请求据此
 * 填 {@code RequestContext}（cartisan SecurityFilter 读 {@code userName}）。</p>
 *
 * @since 0.1.0
 */
@Adapter(PortType.CLIENT)
@Component
public class SaTokenIdpSessionRegistrarAdapter implements IdpSessionRegistrar {

    @Override
    public Instant registerSession(Long userId, String displayName, String accessJwt, long ttlSeconds) {
        // JWT 即 Sa-Token 会话 token 值；timeout 与 access TTL 对齐。
        // 不写响应头（token 走 LoginResponse JSON 体返回）、不种 lasting cookie（IdP SSO cookie 是另一回事，Phase 1）。
        StpUtil.login(userId, new SaLoginParameter()
            .setToken(accessJwt)
            .setTimeout(ttlSeconds)
            .setIsWriteHeader(false)
            .setIsLastingCookie(false));
        // bug#1：cartisan SecurityFilter 读 SaSession.userName 填 RequestContext
        if (displayName != null) {
            StpUtil.getSession().set("userName", displayName);
        }
        return Instant.now().plusSeconds(ttlSeconds);
    }
}
