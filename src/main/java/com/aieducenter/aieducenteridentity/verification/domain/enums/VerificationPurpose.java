package com.aieducenter.aieducenteridentity.verification.domain.enums;

import com.cartisan.core.domain.BaseEnum;
import com.cartisan.data.jpa.converter.BaseEnumConverter;
import jakarta.persistence.Converter;

/**
 * 验证码使用目的。
 *
 * @since 0.1.0
 */
public enum VerificationPurpose implements BaseEnum<VerificationPurpose> {
    /**
     * 用户注册。
     */
    REGISTER(1, "注册"),

    /**
     * 密码重置。
     */
    RESET_PASSWORD(2, "密码重置"),

    /**
     * 用户登录。
     */
    LOGIN(3, "登录");

    private final Integer code;
    private final String name;

    VerificationPurpose(Integer code, String name) {
        this.code = code;
        this.name = name;
    }

    @Override
    public Integer getCode() {
        return code;
    }

    @Override
    public String getName() {
        return name;
    }

    /**
     * JPA 转换器，自动应用。
     */
    @Converter(autoApply = true)
    public static class JpaConverter extends BaseEnumConverter<VerificationPurpose> {
        public JpaConverter() {
            super(VerificationPurpose.class);
        }
    }
}
