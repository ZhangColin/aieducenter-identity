package com.aieducenter.aieducenteridentity.account.endpoints.web;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 管理调用方标记注解——标在 account bc 管理端点（类或方法）上，表示该端点
 * <strong>只放行 {@code identity.management.authorized-callers} 白名单内的签名调用方</strong>
 * （过渡 stopgap，默认 {@code admin-console}；正式 per-app 权限见 #64 / ADR-0010）。
 *
 * <p>与 {@code @RequireSignature}（cartisan-openapi）成对使用——管理端点
 * <b>均 {@code @RequireSignature} + {@code @RequireManagementCaller}</b>：签名 gate 先判认证（401），
 * 本 gate 再判白名单（403）。由 {@link ManagementCallerInterceptor} 兜底（非 AOP 切面，
 * 与签名 interceptor 同模式），注册 order 在签名 interceptor 之后。</p>
 *
 * <p>语义：签名 = 认证（任何注册应用都能签名调用），管理操作 = 需额外授权（只 admin-console 能封号/改密）。
 * 「admin」是调用方维度，不是端点分组维度——管理端点仍归 {@code /api/account/*}，守 ADR-0009「一视同仁」。</p>
 *
 * @since 0.1.0
 * @see ManagementCallerInterceptor
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireManagementCaller {
}
