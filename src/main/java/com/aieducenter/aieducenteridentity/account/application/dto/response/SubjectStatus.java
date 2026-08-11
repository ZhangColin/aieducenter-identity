package com.aieducenter.aieducenteridentity.account.application.dto.response;

/**
 * Subject 读模型的可用性投影（{@link SubjectView#status()}）。
 *
 * <p>account 域内「可用性」是两个独立维度——{@code Account.status}（ACTIVE/DISABLED）+ {@code Account.locked}
 * （布尔）。{@link SubjectView} 是给跨上下文调用方（sso 等）的读模型，把这两维投影成单一可用性枚举，
 * 让调用方自决 gate（ADR-0008：token 路径判 usable 抛、userinfo 路径忽略——保既有差异）。</p>
 *
 * <p>优先级与 {@code Account.ensureLoginable()} 一致——{@link #DISABLED} 优先于 {@link #LOCKED}
 * （同时停用又锁定时按停定论），保证读模型判定与登录路径判定口径相同。</p>
 *
 * @since 0.1.0
 */
public enum SubjectStatus {

    /** 正常可用（ACTIVE 且未锁定）——可登录、可签发 token。 */
    USABLE,

    /** 已停用（运营/安全处置）——登录与 token 签发均被拒。 */
    DISABLED,

    /** 已锁定（临时）——登录与 token 签发均被拒。 */
    LOCKED
}
