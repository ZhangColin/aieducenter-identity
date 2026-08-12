package com.aieducenter.aieducenteridentity.verification.application.dto;

/**
 * 签名服务发码 / 验码端点的渠道判别值（请求体 {@code target}，#62）。
 *
 * <p>签名服务把邮箱、短信两条链路统一到 {@code POST /api/verification-codes}、
 * {@code POST /api/verification-codes/verify} 两个端点，用 {@code target} 路由：
 * {@link #EMAIL} 走 {@code sendEmailVerificationCode/verifyCode}，{@link #SMS} 走
 * {@code sendSmsVerificationCode/verifyPhoneCode}。类型化（而非裸 String）让 Jackson
 * 在反序列化时即挡下非法取值（→ 400），契约自证、OpenAPI 文档清晰。
 *
 * @since 0.1.0
 */
public enum CodeTarget {
    /** 邮箱渠道——{@code value} 为邮箱地址。 */
    EMAIL,
    /** 短信渠道——{@code value} 为手机号。 */
    SMS
}
