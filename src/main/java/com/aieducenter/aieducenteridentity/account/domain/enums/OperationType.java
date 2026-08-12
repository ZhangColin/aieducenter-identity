package com.aieducenter.aieducenteridentity.account.domain.enums;

import com.cartisan.core.domain.BaseEnum;
import com.cartisan.data.jpa.converter.BaseEnumConverter;
import jakarta.persistence.Converter;

/**
 * 账号管理操作类型——后台管理（admin-console）状态变更类操作的审计分类（{@code account_operation_log.op_type}，
 * ADR-0010 / #68）。
 *
 * <p>存数据库为 {@link #code}（Integer）；JPA 经 {@link JpaConverter} 自动转换。本期随审计基建落地
 * 第一个状态变更端点（disable）；后续状态 / 密码操作（解封 / 解锁 / 踢人 / 重置密码 / 清密码 / 强制改密等）
 * 复用本枚举，按各自工单（#69 / #71 / #72）扩展取值。</p>
 *
 * <ul>
 *   <li>{@link #DISABLE}：封号（停用账号），原因必填。</li>
 * </ul>
 *
 * @since 0.1.0
 */
public enum OperationType implements BaseEnum<OperationType> {

    DISABLE(1, "封号");

    private final Integer code;
    private final String name;

    OperationType(Integer code, String name) {
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
    public static class JpaConverter extends BaseEnumConverter<OperationType> {
        public JpaConverter() {
            super(OperationType.class);
        }
    }
}
