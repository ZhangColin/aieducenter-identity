package com.aieducenter.aieducenteridentity.account.application.dto.command;

import jakarta.validation.constraints.Size;

/**
 * 更新个人资料命令（需登录态；字段可空——空表示不修改）。
 *
 * @param nickname 新昵称（可空）
 * @param avatar   新头像 URL（可空）
 */
public record UpdateProfileCommand(
    @Size(max = 50) String nickname,
    @Size(max = 512) String avatar
) {}
