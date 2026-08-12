package com.aieducenter.aieducenteridentity.account.application.dto.response;

import com.aieducenter.aieducenteridentity.account.domain.aggregate.Account;
import com.aieducenter.aieducenteridentity.account.domain.aggregate.Profile;
import com.aieducenter.aieducenteridentity.account.domain.enums.AccountStatus;

/**
 * 账号管理详情读模型——后台管理（admin-console）视角的账号全貌（ADR-0010 / #67）。
 *
 * <p>区别于面向终端应用 / OIDC 的 {@link SubjectView}（只兜「已认证身份数据」+ 可用性投影），
 * 本视图面向运营人员，额外暴露管理动作所需的原始状态维度：</p>
 * <ul>
 *   <li>{@code status}（原始 {@link AccountStatus}，ACTIVE/DISABLED）+ {@code locked}（布尔）
 *       ——<b>分列</b>，与 {@code SubjectStatus}（合并投影 USABLE/DISABLED/LOCKED）不同：
 *       运营需看清「停用」与「系统锁定」两个正交维度；</li>
 *   <li>{@code hasPassword}（是否设过密码，{@code passwordHash != null}）——决定能否走密码登录、
 *       是否需清密码 / 强制改密等后续动作；</li>
 *   <li>资料（email/phone/nickname/avatar）取自 Account + Profile（与 SubjectView 同口径）。</li>
 * </ul>
 *
 * <p>{@code status} 经 cartisan-web {@code BaseEnumSerializer} 序列化为 Integer code（1=ACTIVE / 0=DISABLED）。</p>
 *
 * @param userId      用户 ID（TSID）
 * @param email       邮箱（可空）
 * @param phone       手机号（可空）
 * @param nickname    昵称（取自 Profile，可空）
 * @param avatar      头像 URL（取自 Profile，可空）
 * @param status      账号状态（ACTIVE / DISABLED）
 * @param locked      是否被系统锁定（登录失败累计等，独立于 status）
 * @param hasPassword 是否设过密码（社交/纯验证码账号为 false）
 * @since 0.1.0
 */
public record AccountManagementView(
    Long userId,
    String email,
    String phone,
    String nickname,
    String avatar,
    AccountStatus status,
    boolean locked,
    boolean hasPassword
) {

    /**
     * 由 account 聚合（+ 可空 profile）投影出管理详情。
     *
     * @param account 账号聚合（非空）
     * @param profile 个人资料（可空——新注册 / 未编辑过资料的账号无 Profile）
     * @return 管理详情读模型
     */
    public static AccountManagementView of(Account account, Profile profile) {
        String nickname = profile != null ? profile.getNickname() : null;
        String avatar = profile != null ? profile.getAvatar() : null;
        return new AccountManagementView(
            account.getId(),
            account.getEmail(),
            account.getPhone(),
            nickname,
            avatar,
            account.getStatus(),
            account.isLocked(),
            account.getPasswordHash() != null);
    }
}
