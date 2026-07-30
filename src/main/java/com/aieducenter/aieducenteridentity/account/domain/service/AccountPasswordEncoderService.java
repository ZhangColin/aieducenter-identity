package com.aieducenter.aieducenteridentity.account.domain.service;

import com.cartisan.core.stereotype.DomainService;

/**
 * 密码编码领域服务——封装南向端口 {@link AccountPasswordEncoder}，提供领域层密码处理能力。
 *
 * @since 0.1.0
 */
@DomainService
public class AccountPasswordEncoderService {

    private final AccountPasswordEncoder encoder;

    public AccountPasswordEncoderService(AccountPasswordEncoder encoder) {
        this.encoder = encoder;
    }

    /**
     * 加密明文密码。
     */
    public String encodePassword(String plainPassword) {
        return encoder.encode(plainPassword);
    }

    /**
     * 校验密码。
     *
     * @param plainPassword  明文密码
     * @param encodedPassword hash（可为 null——无密码账号恒返回 false）
     * @return 是否匹配
     */
    public boolean verifyPassword(String plainPassword, String encodedPassword) {
        if (encodedPassword == null) {
            return false;
        }
        return encoder.matches(plainPassword, encodedPassword);
    }
}
