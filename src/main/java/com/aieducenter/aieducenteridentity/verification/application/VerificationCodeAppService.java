package com.aieducenter.aieducenteridentity.verification.application;

import com.aieducenter.aieducenteridentity.shared.error.SharedErrorCode;
import com.aieducenter.aieducenteridentity.verification.application.dto.SendCodeResponse;
import com.aieducenter.aieducenteridentity.verification.application.dto.SendEmailCodeCommand;
import com.aieducenter.aieducenteridentity.verification.application.dto.SendSmsCodeCommand;
import com.aieducenter.aieducenteridentity.verification.application.dto.VerifyCodeCommand;
import com.aieducenter.aieducenteridentity.verification.application.dto.VerifyCodeResult;
import com.aieducenter.aieducenteridentity.verification.application.dto.VerifySmsCodeCommand;
import com.aieducenter.aieducenteridentity.verification.config.VerificationCodeProperties;
import com.aieducenter.aieducenteridentity.verification.domain.enums.VerificationPurpose;
import com.aieducenter.aieducenteridentity.verification.domain.enums.VerificationType;
import com.aieducenter.aieducenteridentity.verification.domain.error.VerificationCodeError;
import com.aieducenter.aieducenteridentity.verification.domain.aggregate.VerificationCode;
import com.aieducenter.aieducenteridentity.verification.domain.repository.VerificationCodeRepository;
import com.aieducenter.aieducenteridentity.verification.domain.service.VerificationCodeGenerationService;
import com.aieducenter.aieducenteridentity.verification.domain.service.MessageSender;
import cn.hutool.core.lang.Validator;
import com.cartisan.core.exception.ApplicationException;
import com.cartisan.core.exception.DomainException;
import com.cartisan.core.util.Assertions;

import org.springframework.stereotype.Service;

/**
 * 验证码应用服务。
 *
 * @since 0.1.0
 */
@Service
public class VerificationCodeAppService {

    private final VerificationCodeRepository repository;
    private final VerificationCodeGenerationService generator;
    private final MessageSender messageSender;
    private final VerificationCodeProperties properties;
    private final CaptchaAppService captchaAppService;

    public VerificationCodeAppService(
            VerificationCodeRepository repository,
            VerificationCodeGenerationService generator,
            MessageSender messageSender,
            VerificationCodeProperties properties,
            CaptchaAppService captchaAppService) {
        this.repository = repository;
        this.generator = generator;
        this.messageSender = messageSender;
        this.properties = properties;
        this.captchaAppService = captchaAppService;
    }

    /**
     * 发送邮箱验证码。
     *
     * @param command 命令
     * @param ip 客户端IP
     * @return 发送结果
     * @throws DomainException 邮箱格式错误、限流触发
     */
    public SendCodeResponse sendEmailVerificationCode(SendEmailCodeCommand command, String ip) {
        // 1. 校验邮箱格式
        validateEmailFormat(command.email());

        // 2. 校验 purpose
        VerificationPurpose purpose = validatePurpose(command.purpose());

        // 3. 原子操作：检查并获取邮箱限流锁
        Assertions.require(
            repository.tryAcquireEmailLock(command.email(), command.purpose()),
            VerificationCodeError.RATE_LIMIT_EMAIL
        );

        // 4. 原子操作：检查并增加 IP 计数
        long ipCount = repository.checkAndIncrementIp(ip);
        Assertions.require(
            ipCount <= properties.getIpMaxPerHour(),
            VerificationCodeError.RATE_LIMIT_IP
        );

        // 5. 生成验证码
        String code = generator.generate();

        // 6. 创建验证码实体
        VerificationCode verificationCode = VerificationCode.create(
            VerificationType.EMAIL,
            command.email(),
            code,
            purpose
        );

        // 7. 保存到 Redis
        repository.save(verificationCode);

        // 8. 发送邮件
        messageSender.send(command.email(), code, purpose);

        int expireSeconds = properties.getExpireMinutes() * 60;
        long cooldownSeconds = properties.getEmailCooldownSeconds();
        return new SendCodeResponse(expireSeconds, (int) cooldownSeconds);
    }

    /**
     * 校验验证码。
     *
     * @param command 命令
     * @return 校验结果
     * @throws DomainException 验证码错误、已过期、已使用
     */
    public VerifyCodeResult verifyCode(VerifyCodeCommand command) {
        // 1. 校验邮箱格式
        validateEmailFormat(command.email());

        // 2. 校验 purpose
        VerificationPurpose purpose = validatePurpose(command.purpose());

        // 3. 原子操作：验证并标记为已使用
        String id = command.email() + ":" + purpose.name();
        boolean verified = repository.verifyAndMarkAsUsed(id, command.code());

        if (!verified) {
            // 验证失败，尝试获取详细错误信息
            var verificationCode = repository.findById(id);
            if (verificationCode.isEmpty()) {
                throw new DomainException(SharedErrorCode.VERIFICATION_CODE_INVALID);
            }
            var code = verificationCode.get();
            if (code.isUsed()) {
                throw new DomainException(VerificationCodeError.CODE_ALREADY_USED);
            }
            if (java.time.Instant.now().isAfter(code.getExpireAt())) {
                throw new DomainException(VerificationCodeError.CODE_EXPIRED);
            }
            throw new DomainException(SharedErrorCode.VERIFICATION_CODE_INVALID);
        }

        return new VerifyCodeResult(true, "验证码正确");
    }

    /**
     * 发送短信验证码。
     *
     * @param command 命令
     * @param ip 客户端IP
     * @return 发送结果
     * @throws DomainException 手机号格式错误、限流触发
     */
    public SendCodeResponse sendSmsVerificationCode(SendSmsCodeCommand command, String ip) {
        // 1. 校验手机号格式
        validatePhoneFormat(command.phone());

        // 校验图形验证码：提供方才校验。签名服务路径（#62）图形码可选——调用方可信、防轰炸靠限流
        // （per apiKey + per target），不强制图形码；SSO 浏览器闭环经 SsoVerificationCodeController
        // 的 @Valid + SendSmsCodeCommand.@NotBlank 强制必带，到这里时 captchaId 必非空。
        if (command.captchaId() != null && !command.captchaId().isBlank()) {
            captchaAppService.verifyCaptcha(command.captchaId(), command.captchaCode());
        }

        // 2. 校验 purpose
        VerificationPurpose purpose = validatePurpose(command.purpose());

        // 3. 原子操作：检查并获取手机限流锁
        Assertions.require(
            repository.tryAcquirePhoneLock(command.phone(), command.purpose()),
            VerificationCodeError.RATE_LIMIT_PHONE
        );

        // 4. 原子操作：检查并增加 IP 计数
        long ipCount = repository.checkAndIncrementIp(ip);
        Assertions.require(
            ipCount <= properties.getIpMaxPerHour(),
            VerificationCodeError.RATE_LIMIT_IP
        );

        // 5. 生成验证码
        String code = generator.generate();

        // 6. 创建验证码实体
        VerificationCode verificationCode = VerificationCode.create(
            VerificationType.SMS,
            command.phone(),
            code,
            purpose
        );

        // 7. 保存到 Redis
        repository.save(verificationCode);

        // 8. 发送短信
        messageSender.send(command.phone(), code, purpose);

        int expireSeconds = properties.getExpireMinutes() * 60;
        long cooldownSeconds = properties.getPhoneCooldownSeconds();
        return new SendCodeResponse(expireSeconds, (int) cooldownSeconds);
    }

    /**
     * 校验短信验证码。
     *
     * @param command 命令
     * @return 校验结果
     * @throws DomainException 验证码错误、已过期、已使用
     */
    public VerifyCodeResult verifyPhoneCode(VerifySmsCodeCommand command) {
        // 1. 校验手机号格式
        validatePhoneFormat(command.phone());

        // 2. 校验 purpose
        VerificationPurpose purpose = validatePurpose(command.purpose());

        // 3. 原子操作：验证并标记为已使用
        String id = command.phone() + ":" + purpose.name();
        boolean verified = repository.verifyAndMarkAsUsed(id, command.code());

        if (!verified) {
            // 验证失败，尝试获取详细错误信息
            var verificationCode = repository.findById(id);
            if (verificationCode.isEmpty()) {
                throw new DomainException(SharedErrorCode.VERIFICATION_CODE_INVALID);
            }
            var code = verificationCode.get();
            if (code.isUsed()) {
                throw new DomainException(VerificationCodeError.CODE_ALREADY_USED);
            }
            if (java.time.Instant.now().isAfter(code.getExpireAt())) {
                throw new DomainException(VerificationCodeError.CODE_EXPIRED);
            }
            throw new DomainException(SharedErrorCode.VERIFICATION_CODE_INVALID);
        }

        return new VerifyCodeResult(true, "验证码正确");
    }

    /**
     * 校验邮箱格式。
     *
     * <p>使用简化但合理的邮箱验证规则：
     * <ul>
     *   <li>本地部分：字母、数字及 ._-%+ 字符</li>
     *   <li>域名部分：至少一个点，TLD 至少 2 个字符</li>
     *   <li>不允许连续的点或特殊字符开头/结尾</li>
     * </ul>
     */
    private void validateEmailFormat(String email) {
        if (email == null || !email.matches("^[a-zA-Z0-9]([a-zA-Z0-9._%+-]*[a-zA-Z0-9])?@[a-zA-Z0-9]([a-zA-Z0-9-]*[a-zA-Z0-9])?(\\.[a-zA-Z0-9]([a-zA-Z0-9-]*[a-zA-Z0-9])?)+$")) {
            throw new DomainException(VerificationCodeError.EMAIL_INVALID);
        }
    }

    /**
     * 校验手机号格式。
     */
    private void validatePhoneFormat(String phone) {
        if (phone == null || !Validator.isMobile(phone)) {
            throw new DomainException(VerificationCodeError.PHONE_INVALID);
        }
    }

    /**
     * 校验并转换 purpose。
     */
    private VerificationPurpose validatePurpose(String purpose) {
        if (purpose == null) {
            throw new ApplicationException(VerificationCodeError.PURPOSE_INVALID);
        }
        try {
            return VerificationPurpose.valueOf(purpose.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ApplicationException(VerificationCodeError.PURPOSE_INVALID);
        }
    }
}
