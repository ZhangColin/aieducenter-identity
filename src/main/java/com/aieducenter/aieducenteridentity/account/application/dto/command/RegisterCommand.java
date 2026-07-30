package com.aieducenter.aieducenteridentity.account.application.dto.command;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 注册命令（开放注册：至少留 email 或 phone 之一，填了即当场发码验证、验过才建号）。
 *
 * <p>字段都可空——「至少一个联络方式 + 对应验证码」由应用服务校验（Bean Validation 难以表达跨字段约束）。
 * {@code password} 可空（纯验证码/社交账号无密码）；非空时须满足强度。</p>
 *
 * @param email              邮箱（可空，填了则需 emailVerificationCode）
 * @param phone              手机号（可空，填了则需 smsVerificationCode）
 * @param password           明文密码（可空）
 * @param nickname           昵称（可空）
 * @param emailVerificationCode 邮箱验证码（email 非空时必填）
 * @param smsVerificationCode   短信验证码（phone 非空时必填）
 */
public record RegisterCommand(
    @Email String email,
    @Pattern(regexp = "^1[3-9]\\d{9}$") String phone,
    @Size(min = 8, max = 20) @Pattern(regexp = "^(?=.*[a-zA-Z])(?=.*\\d).+$") String password,
    @Size(max = 50) String nickname,
    String emailVerificationCode,
    String smsVerificationCode
) {}
