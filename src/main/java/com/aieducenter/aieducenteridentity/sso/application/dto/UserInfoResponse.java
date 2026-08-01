package com.aieducenter.aieducenteridentity.sso.application.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/**
 * {@code /userinfo} 响应——OIDC UserInfo Claims（按授权 scope 过滤，issue #17）。
 *
 * <p>{@code sub} 恒返回；其余声明仅在对应 scope 授权时填入，未授权字段为 null 并由
 * {@link JsonInclude.Include#NON_NULL} 省略（OIDC 惯例，不发 null）。</p>
 *
 * <ul>
 *   <li>{@code profile} → {@code nickname} / {@code picture}</li>
 *   <li>{@code email} → {@code email} / {@code email_verified}</li>
 *   <li>{@code phone} → {@code phone_number} / {@code phone_number_verified}</li>
 * </ul>
 *
 * @param sub                   主题（= userId 字符串）
 * @param nickname              昵称（profile scope）
 * @param picture               头像 URL（profile scope）
 * @param email                 邮箱（email scope）
 * @param emailVerified         邮箱是否已验证（email scope）
 * @param phoneNumber           手机号（phone scope）
 * @param phoneNumberVerified   手机号是否已验证（phone scope）
 *
 * @since 0.1.0
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record UserInfoResponse(
    String sub,
    String nickname,
    String picture,
    String email,
    Boolean emailVerified,
    String phoneNumber,
    Boolean phoneNumberVerified
) {
}
