package com.aieducenter.aieducenteridentity.account.application.dto.query;

import java.time.LocalDateTime;

import org.springframework.format.annotation.DateTimeFormat;

import com.aieducenter.aieducenteridentity.account.domain.enums.AccountStatus;
import com.cartisan.data.jpa.specification.Condition;
import com.cartisan.data.jpa.specification.ConditionType;

/**
 * 账号搜索查询条件——后台管理（admin-console）多条件组合分页（#70）。
 *
 * <p>配合 {@link com.cartisan.data.jpa.specification.ConditionSpecifications#fromAnnotation(Object)}
 * 拼成 JPA {@link org.springframework.data.jpa.domain.Specification}——首次启用 {@code BaseRepository}
 * 自带的 {@code JpaSpecificationExecutor} 分页查询（#70；分页请求 #78 起走框架 Pagination）。
 * {@code @Condition} 字段 null / 空串自动跳过：不传即不过滤，多字段 AND 组合。</p>
 *
 * <h3>条件映射</h3>
 * <ul>
 *   <li>{@code userId}（EQUAL，{@code propName=id}——实体主键叫 {@code id}，外部查询参数叫 {@code userId}）；</li>
 *   <li>{@code email} / {@code phone}（INNER_LIKE，模糊匹配联络方式）；</li>
 *   <li>{@code status}（EQUAL，{@link AccountStatus}；MVC 经 BaseEnum 按 code 绑定：1=ACTIVE / 0=DISABLED）；</li>
 *   <li>{@code locked}（EQUAL，布尔；不传 = 不过滤，传 {@code false} = 只看未锁）；</li>
 *   <li>{@code createdFrom} / {@code createdTo}（注册时间区间——{@code createdAt} GREATER_EQUAL / LESS_EQUAL 两界；
 *       Account 继承 {@code Auditable} 的 {@code createdAt} 即注册时间，两端可单用；
 *       {@code @DateTimeFormat(ISO.DATE_TIME)} 约束入参格式，如 {@code 2020-01-01T00:00:00}）。</li>
 * </ul>
 *
 * <p>{@code page} / {@code size} / {@code sort} 不在此 DTO，由框架 {@link com.cartisan.web.request.Pagination}
 * 在 controller 并列绑定（1-based + clamp，#78 起）。{@code createdFrom > createdTo} 不报错——返回空页（无行命中）。</p>
 *
 * @since 0.1.0
 */
public record AccountSearchQuery(
    @Condition(propName = "id", type = ConditionType.EQUAL) Long userId,
    @Condition(type = ConditionType.INNER_LIKE) String email,
    @Condition(type = ConditionType.INNER_LIKE) String phone,
    @Condition(type = ConditionType.EQUAL) AccountStatus status,
    @Condition(type = ConditionType.EQUAL) Boolean locked,
    @Condition(propName = "createdAt", type = ConditionType.GREATER_EQUAL)
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime createdFrom,
    @Condition(propName = "createdAt", type = ConditionType.LESS_EQUAL)
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime createdTo
) {
}
