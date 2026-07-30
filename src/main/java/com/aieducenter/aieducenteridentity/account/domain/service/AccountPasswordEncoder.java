package com.aieducenter.aieducenteridentity.account.domain.service;

import com.cartisan.core.stereotype.Port;
import com.cartisan.core.stereotype.PortType;

/**
 * 账号密码编码器端口（南向，对基础设施服务的能力抽象）。
 *
 * @since 0.1.0
 */
@Port(PortType.CLIENT)
public interface AccountPasswordEncoder {

    /**
     * 加密明文密码。
     *
     * @param plainPassword 明文密码
     * @return 加密后的密码 hash
     */
    String encode(String plainPassword);

    /**
     * 校验明文密码与 hash 是否匹配。
     *
     * @param plainPassword  明文密码
     * @param encodedPassword hash（可为 null——无密码账号恒不匹配）
     * @return 是否匹配
     */
    boolean matches(String plainPassword, String encodedPassword);
}
