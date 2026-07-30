package com.aieducenter.aieducenteridentity.account.domain.enums;

import com.cartisan.core.domain.BaseEnum;
import com.cartisan.data.jpa.converter.BaseEnumConverter;
import jakarta.persistence.Converter;

/**
 * 账号状态。
 *
 * <p>存数据库为 {@link #code}（Integer）；JPA 经 {@link JpaConverter} 自动转换、Jackson 序列化为
 * int、Spring MVC 支持 int↔enum 绑定（见 {@link BaseEnum}）。</p>
 *
 * <ul>
 *   <li>{@link #ACTIVE}：正常，可登录。</li>
 *   <li>{@link #DISABLED}：停用（运营/安全处置），登录被拒。</li>
 * </ul>
 *
 * <p>「锁定」是独立维度，用 {@code Account.locked} 布尔字段表达（临时锁定 vs 永久停用语义不同）。</p>
 */
public enum AccountStatus implements BaseEnum<AccountStatus> {

    ACTIVE(1, "正常"),
    DISABLED(0, "停用");

    private final Integer code;
    private final String name;

    AccountStatus(Integer code, String name) {
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
     * JPA 转换器（autoApply = true，实体字段无需 @Convert）。
     *
     * <p>类名用 {@code JpaConverter} 而非 {@code Converter}，避免与 {@code @Converter} 注解命名冲突。</p>
     */
    @Converter(autoApply = true)
    public static class JpaConverter extends BaseEnumConverter<AccountStatus> {
        public JpaConverter() {
            super(AccountStatus.class);
        }
    }
}
