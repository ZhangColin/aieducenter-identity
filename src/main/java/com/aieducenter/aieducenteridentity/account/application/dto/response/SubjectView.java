package com.aieducenter.aieducenteridentity.account.application.dto.response;

import com.aieducenter.aieducenteridentity.account.domain.aggregate.Account;
import com.aieducenter.aieducenteridentity.account.domain.aggregate.Profile;
import com.aieducenter.aieducenteridentity.account.domain.enums.AccountStatus;

/**
 * Subject 读模型——account 应用层对外兜出的「已认证身份数据」契约（ADR-0007/0008）。
 *
 * <p>sso（及未来非-SSO 应用）经 {@code AccountAuthAppService}（authenticate / register）或
 * {@code AccountSubjectAppService}（subjectClaims）取得本视图，用来造 OIDC 声明（token / userinfo），
 * 不直接碰 {@link Account}/{@link Profile} 聚合。</p>
 *
 * <ul>
 *   <li>{@code userId}（= SSO {@code sub}，稳定身份本体）</li>
 *   <li>{@code email} / {@code phone}（登录定位 + 联络通道，可空）</li>
 *   <li>{@code nickname} / {@code avatar}（取自 Profile，可空——新注册尚无 Profile）</li>
 *   <li>{@code status}（可用性投影，{@link SubjectStatus}）——<b>调用方自决 gate</b>：
 *       token 路径判 usable 抛、userinfo 路径忽略（保既有 {@code ensureLoginable} vs {@code ensureUsable} 差异）</li>
 * </ul>
 *
 * @param userId   用户 ID（TSID，作 SSO sub）
 * @param email    邮箱（可空）
 * @param phone    手机号（可空）
 * @param nickname 昵称（取自 Profile，可空）
 * @param avatar   头像 URL（取自 Profile，可空）
 * @param status   可用性投影（USABLE / DISABLED / LOCKED）
 * @since 0.1.0
 */
public record SubjectView(
    Long userId,
    String email,
    String phone,
    String nickname,
    String avatar,
    SubjectStatus status
) {

    /**
     * 由 account 聚合（+ 可空 profile）投影出 SubjectView。
     *
     * <p>集中领域 → 读模型的映射（含可用性优先级判定），供 {@code AccountAuthAppService} /
     * {@code AccountSubjectAppService} 共用，避免可用性判定逻辑散落两处。</p>
     *
     * @param account 账号聚合（非空）
     * @param profile 个人资料（可空——新注册 / 未编辑过资料的账号无 Profile）
     * @return SubjectView
     */
    public static SubjectView of(Account account, Profile profile) {
        String nickname = profile != null ? profile.getNickname() : null;
        String avatar = profile != null ? profile.getAvatar() : null;
        return new SubjectView(
            account.getId(),
            account.getEmail(),
            account.getPhone(),
            nickname,
            avatar,
            availabilityOf(account));
    }

    /**
     * 可用性投影——优先级与 {@code Account.ensureLoginable()} 一致：停用优先于锁定。
     */
    private static SubjectStatus availabilityOf(Account account) {
        if (account.getStatus() == AccountStatus.DISABLED) {
            return SubjectStatus.DISABLED;
        }
        if (account.isLocked()) {
            return SubjectStatus.LOCKED;
        }
        return SubjectStatus.USABLE;
    }
}
