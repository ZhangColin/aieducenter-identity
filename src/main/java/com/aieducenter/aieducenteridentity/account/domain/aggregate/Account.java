package com.aieducenter.aieducenteridentity.account.domain.aggregate;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import com.aieducenter.aieducenteridentity.account.domain.enums.AccountStatus;
import com.aieducenter.aieducenteridentity.account.domain.error.AccountError;
import com.cartisan.core.domain.AggregateRoot;
import com.cartisan.core.exception.DomainException;
import com.cartisan.core.stereotype.Aggregate;
import com.cartisan.core.util.Assertions;
import com.cartisan.data.jpa.domain.AuditableSoftDeletable;
import com.cartisan.data.jpa.id.TsidGenerator;

import lombok.Getter;

/**
 * Account 聚合根——平台终端用户的身份本体。
 *
 * <h3>锚点</h3>
 * {@code id}（= userId，TSID）是唯一稳定身份，作 SSO {@code sub}。换邮箱/换手机它都不变。
 *
 * <h3>登录定位字段</h3>
 * {@code email} / {@code phone}（均可空、全局唯一）是「登录入口 + 联络通道」，不是身份本体——
 * 用来「找到是哪个用户」。{@code passwordHash} 可空（社交/纯验证码账号无密码）。
 *
 * <h3>状态</h3>
 * {@code status}（ACTIVE/DISABLED）+ {@code locked}（布尔）+ {@code lastLoginAt}。
 *
 * <h3>与 studio 的差异（ADR-0001）</h3>
 * 丢掉了 {@code username} 登录键；{@code passwordHash} 改可空；个人资料拆到 {@link Profile}。
 *
 * @since 0.1.0
 */
@Entity
@Table(name = "act_account")
@Aggregate
public class Account extends AuditableSoftDeletable implements AggregateRoot<Account, Long> {

    @Getter
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Getter
    @Column(name = "email", length = 255)
    private String email;

    @Getter
    @Column(name = "phone", length = 20)
    private String phone;

    @Getter
    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    @Getter
    @Column(name = "status", nullable = false)
    private AccountStatus status;

    @Getter
    @Column(name = "locked", nullable = false)
    private boolean locked;

    @Getter
    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    /**
     * 注册新账号。
     *
     * <p>至少留一个联络方式（email 或 phone），由调用方保证已「当场发码验证、验过才建号」。
     * {@code encodedPassword} 可空（纯验证码/社交账号无密码）。</p>
     *
     * @param email           邮箱（可空）
     * @param phone           手机号（可空）
     * @param encodedPassword 已加密密码（可空）
     * @return 新账号
     */
    public static Account register(String email, String phone, String encodedPassword) {
        boolean hasEmail = email != null && !email.isBlank();
        boolean hasPhone = phone != null && !phone.isBlank();
        Assertions.require(hasEmail || hasPhone, AccountError.CONTACT_REQUIRED);

        Account account = new Account();
        account.email = hasEmail ? email : null;
        account.phone = hasPhone ? phone : null;
        account.passwordHash = encodedPassword;
        account.status = AccountStatus.ACTIVE;
        account.locked = false;
        return account;
    }

    /**
     * 从持久化恢复（仅基础设施层使用）。
     */
    public static Account restore(Long id, String email, String phone, String passwordHash,
                                  AccountStatus status, boolean locked, LocalDateTime lastLoginAt) {
        Account account = new Account();
        account.id = id;
        account.email = email;
        account.phone = phone;
        account.passwordHash = passwordHash;
        account.status = status;
        account.locked = locked;
        account.lastLoginAt = lastLoginAt;
        return account;
    }

    protected Account() {
        // JPA required
    }

    /**
     * JPA 保存前生成 userId（TSID）。
     */
    @PrePersist
    void prePersist() {
        if (id == null) {
            this.id = TsidGenerator.newInstance().generate();
        }
    }

    // ========== 登录态 ==========

    /**
     * 断言账号可登录：停用 / 锁定则抛领域异常（具体原因，便于告知已通过身份验证的用户）。
     *
     * <p>应在密码/验证码校验通过后调用——身份已证明，告知停用/锁定不构成用户枚举。</p>
     */
    public void ensureLoginable() {
        if (status == AccountStatus.DISABLED) {
            throw new DomainException(AccountError.ACCOUNT_DISABLED);
        }
        if (locked) {
            throw new DomainException(AccountError.ACCOUNT_LOCKED);
        }
    }

    /**
     * 记录登录时间。
     */
    public void recordLogin() {
        this.lastLoginAt = LocalDateTime.now();
    }

    /**
     * 显示标签：昵称（非空）优先，其次邮箱，其次手机号。供 SSO 会话显示名 / token 写入。
     *
     * @param nickname 个人资料昵称（可空）
     */
    public String displayLabel(String nickname) {
        if (nickname != null && !nickname.isBlank()) {
            return nickname;
        }
        return email != null ? email : phone;
    }

    // ========== 密码管理 ==========

    /**
     * 修改密码（应用服务已验证旧密码）。
     *
     * @param newEncodedPassword 新密码（已加密）
     */
    public void changePassword(String newEncodedPassword) {
        Assertions.require(newEncodedPassword != null, AccountError.PASSWORD_WEAK);
        this.passwordHash = newEncodedPassword;
    }

    /**
     * 重置密码（无需旧密码，找回密码场景）。
     *
     * @param newEncodedPassword 新密码（已加密）
     */
    public void resetPassword(String newEncodedPassword) {
        Assertions.require(newEncodedPassword != null, AccountError.PASSWORD_WEAK);
        this.passwordHash = newEncodedPassword;
    }

    // ========== 状态变更 ==========

    /**
     * 停用账号。
     */
    public void disable() {
        this.status = AccountStatus.DISABLED;
    }

    /**
     * 激活账号。
     */
    public void activate() {
        this.status = AccountStatus.ACTIVE;
    }

    /**
     * 锁定账号。
     */
    public void lock() {
        this.locked = true;
    }

    /**
     * 解锁账号。
     */
    public void unlock() {
        this.locked = false;
    }
}
