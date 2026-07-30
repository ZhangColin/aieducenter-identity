package com.aieducenter.aieducenteridentity.account.domain.aggregate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import com.cartisan.core.domain.AggregateRoot;
import com.cartisan.core.stereotype.Aggregate;
import com.cartisan.data.jpa.domain.AuditableSoftDeletable;

import lombok.Getter;
import lombok.Setter;

/**
 * Profile 聚合根——个人信息扩展表，与 {@link Account} 1:1（以 userId 为主键）。
 *
 * <p>昵称/头像等个人资料不塞进用户主表，单独建表便于以后扩展（ADR-0001）。</p>
 *
 * @since 0.1.0
 */
@Entity
@Table(name = "act_profile")
@Aggregate
public class Profile extends AuditableSoftDeletable implements AggregateRoot<Profile, Long> {

    @Getter
    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    /**
     * 聚合根标识（= userId，1:1 指向 Account）。
     */
    @Override
    public Long getId() {
        return userId;
    }

    @Getter
    @Setter
    @Column(name = "nickname", length = 50)
    private String nickname;

    @Getter
    @Setter
    @Column(name = "avatar", length = 512)
    private String avatar;

    /**
     * 创建个人资料。
     *
     * @param userId  用户 ID（与 Account 1:1）
     * @param nickname 昵称（可空，为空时由调用方决定回退值）
     * @param avatar  头像（可空）
     * @return Profile 实例
     */
    public static Profile create(Long userId, String nickname, String avatar) {
        Profile profile = new Profile();
        profile.userId = userId;
        profile.nickname = nickname;
        profile.avatar = avatar;
        return profile;
    }

    /**
     * 从持久化恢复（仅基础设施层使用）。
     */
    public static Profile restore(Long userId, String nickname, String avatar) {
        return create(userId, nickname, avatar);
    }

    protected Profile() {
        // JPA required
    }

    /**
     * 更新个人资料——仅更新非空字段（null 字段保持不变）。
     *
     * @param nickname 新昵称（null/空则不修改）
     * @param avatar   新头像（null/空则不修改）
     */
    public void update(String nickname, String avatar) {
        if (nickname != null && !nickname.isBlank()) {
            this.nickname = nickname;
        }
        if (avatar != null && !avatar.isBlank()) {
            this.avatar = avatar;
        }
    }
}
