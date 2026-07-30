package com.aieducenter.aieducenteridentity.verification.domain.aggregate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import com.aieducenter.aieducenteridentity.verification.domain.enums.VerificationPurpose;
import com.aieducenter.aieducenteridentity.verification.domain.enums.VerificationType;
import com.aieducenter.aieducenteridentity.verification.domain.error.VerificationCodeError;
import com.cartisan.core.domain.AggregateRoot;
import com.cartisan.core.exception.DomainException;
import com.cartisan.core.util.Assertions;

/**
 * 验证码聚合根。
 *
 * <h3>职责</h3>
 * <ul>
 *   <li>封装验证码状态和行为</li>
 *   <li>管理验证码校验规则</li>
 *   <li>管理使用状态</li>
 * </ul>
 *
 * <h3>不变量</h3>
 * <ul>
 *   <li>验证码为 6 位数字</li>
 *   <li>有效期 5 分钟</li>
 *   <li>已使用的验证码不能再次使用</li>
 * </ul>
 *
 * @since 0.1.0
 */
public class VerificationCode implements AggregateRoot<VerificationCode, String> {

    private static final int EXPIRE_MINUTES = 5;
    private static final String CODE_PATTERN = "^\\d{6}$";

    private String id;
    private VerificationType type;
    private String target;
    private String code;
    private VerificationPurpose purpose;
    private Instant expireAt;
    private boolean used;
    private Instant createdAt;

    /**
     * 创建新验证码。
     *
     * @param type 验证码类型
     * @param target 目标（邮箱或手机号）
     * @param code 验证码（6位数字）
     * @param purpose 使用目的
     * @return 验证码聚合根
     */
    public static VerificationCode create(
            VerificationType type,
            String target,
            String code,
            VerificationPurpose purpose) {

        Assertions.require(code != null && code.matches(CODE_PATTERN),
            VerificationCodeError.CODE_INVALID);

        VerificationCode verificationCode = new VerificationCode();
        verificationCode.id = generateId(target, purpose);
        verificationCode.type = type;
        verificationCode.target = target;
        verificationCode.code = code;
        verificationCode.purpose = purpose;
        verificationCode.expireAt = Instant.now().plus(EXPIRE_MINUTES, ChronoUnit.MINUTES);
        verificationCode.used = false;
        verificationCode.createdAt = Instant.now();
        return verificationCode;
    }

    /**
     * 从 Redis 恢复验证码。
     *
     * @param id 唯一标识
     * @param type 类型
     * @param target 目标
     * @param code 验证码
     * @param expireAt 过期时间
     * @param used 是否已使用
     * @param purpose 使用目的
     * @return 验证码聚合根
     */
    public static VerificationCode restore(
            String id,
            VerificationType type,
            String target,
            String code,
            Instant expireAt,
            boolean used,
            VerificationPurpose purpose) {

        VerificationCode verificationCode = new VerificationCode();
        verificationCode.id = id;
        verificationCode.type = type;
        verificationCode.target = target;
        verificationCode.code = code;
        verificationCode.expireAt = expireAt;
        verificationCode.used = used;
        verificationCode.purpose = purpose;
        verificationCode.createdAt = Instant.now();
        return verificationCode;
    }

    /**
     * JPA 默认构造函数。
     */
    protected VerificationCode() {
    }

    /**
     * 校验验证码。
     *
     * @param inputCode 输入的验证码
     * @return 是否有效（码匹配 + 未过期 + 未使用）
     */
    public boolean isValid(String inputCode) {
        if (used) {
            return false;
        }
        if (Instant.now().isAfter(expireAt)) {
            return false;
        }
        return code.equals(inputCode);
    }

    /**
     * 标记为已使用。
     *
     * @throws DomainException 如果已使用
     */
    public void markAsUsed() {
        Assertions.require(!used, VerificationCodeError.CODE_ALREADY_USED);
        this.used = true;
    }

    /**
     * 生成验证码唯一标识。
     *
     * @param target 目标
     * @param purpose 目的
     * @return 唯一标识
     */
    private static String generateId(String target, VerificationPurpose purpose) {
        return target + ":" + purpose.name();
    }

    // ========== Getter ==========

    public String getId() {
        return id;
    }

    public VerificationType getType() {
        return type;
    }

    public String getTarget() {
        return target;
    }

    public String getCode() {
        return code;
    }

    public VerificationPurpose getPurpose() {
        return purpose;
    }

    public Instant getExpireAt() {
        return expireAt;
    }

    public boolean isUsed() {
        return used;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public boolean sameIdentityAs(VerificationCode other) {
        if (other == null) {
            return false;
        }
        return this.id.equals(other.id);
    }
}
