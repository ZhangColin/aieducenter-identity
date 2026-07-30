package com.aieducenter.aieducenteridentity.verification.domain.repository;

import java.util.Optional;

import com.cartisan.core.stereotype.Port;
import com.cartisan.core.stereotype.PortType;
import com.aieducenter.aieducenteridentity.verification.domain.aggregate.VerificationCode;

/**
 * 验证码仓储接口。
 *
 * @since 0.1.0
 */
@Port(PortType.REPOSITORY)
public interface VerificationCodeRepository {

    /**
     * 保存验证码。
     *
     * @param code 验证码
     */
    void save(VerificationCode code);

    /**
     * 查找验证码。
     *
     * @param id 验证码标识
     * @return 验证码
     */
    Optional<VerificationCode> findById(String id);

    /**
     * 原子操作：验证码校验并标记为已使用。
     *
     * <p>此方法使用 Lua 脚本确保原子性，防止并发竞态条件。
     *
     * @param id 验证码标识
     * @param inputCode 输入的验证码
     * @return 验证结果（true=验证成功并已标记，false=验证失败）
     */
    boolean verifyAndMarkAsUsed(String id, String inputCode);

    /**
     * 尝试获取邮箱限流锁（原子操作）。
     *
     * @param email 邮箱
     * @param purpose 目的
     * @return 是否成功获取锁（false 表示在限流期内）
     */
    boolean tryAcquireEmailLock(String email, String purpose);

    /**
     * 尝试获取手机限流锁（原子操作）。
     *
     * @param phone 手机号
     * @param purpose 目的
     * @return 是否成功获取锁（false 表示在限流期内）
     */
    boolean tryAcquirePhoneLock(String phone, String purpose);

    /**
     * 检查并增加 IP 计数（原子操作）。
     *
     * @param ip IP地址
     * @return 当前计数
     */
    long checkAndIncrementIp(String ip);

}
