package com.aieducenter.aieducenteridentity.account.application.dto.response;

/**
 * 个人资料响应。
 *
 * @param userId   用户 ID
 * @param email    邮箱（可空）
 * @param phone    手机号（可空）
 * @param nickname 昵称
 * @param avatar   头像 URL（可空）
 */
public record AccountProfileResponse(
    Long userId,
    String email,
    String phone,
    String nickname,
    String avatar
) {}
