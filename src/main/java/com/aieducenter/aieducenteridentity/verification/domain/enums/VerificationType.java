package com.aieducenter.aieducenteridentity.verification.domain.enums;

import com.cartisan.core.domain.BaseEnum;
import com.cartisan.data.jpa.converter.BaseEnumConverter;
import jakarta.persistence.Converter;

/**
 * 验证码类型。
 *
 * @since 0.1.0
 */
public enum VerificationType implements BaseEnum<VerificationType> {
    /**
     * 邮箱验证码。
     */
    EMAIL(1, "邮箱"),

    /**
     * 短信验证码（预留）。
     */
    SMS(2, "短信"),

    /**
     * 图形验证码。
     */
    CAPTCHA(3, "图形验证码");

    private final Integer code;
    private final String name;

    VerificationType(Integer code, String name) {
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
    public static class JpaConverter extends BaseEnumConverter<VerificationType> {
        public JpaConverter() {
            super(VerificationType.class);
        }
    }
}
