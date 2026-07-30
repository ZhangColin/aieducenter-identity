package com.aieducenter.aieducenteridentity.account.infrastructure.verification;

import org.springframework.stereotype.Component;

import com.aieducenter.aieducenteridentity.verification.application.VerificationCodeAppService;
import com.aieducenter.aieducenteridentity.verification.application.dto.VerifyCodeCommand;
import com.aieducenter.aieducenteridentity.verification.application.dto.VerifySmsCodeCommand;
import com.cartisan.core.stereotype.Adapter;
import com.cartisan.core.stereotype.PortType;

/**
 * 验证码适配器——适配 verification 上下文的 {@link VerificationCodeAppService}，
 * 实现 {@link VerificationCodePort}。
 *
 * @since 0.1.0
 */
@Adapter(PortType.CLIENT)
@Component
public class VerificationCodeAdapter implements VerificationCodePort {

    private final VerificationCodeAppService verificationCodeAppService;

    public VerificationCodeAdapter(VerificationCodeAppService verificationCodeAppService) {
        this.verificationCodeAppService = verificationCodeAppService;
    }

    @Override
    public void verifyPhoneCode(String phone, String code, String purpose) {
        verificationCodeAppService.verifyPhoneCode(new VerifySmsCodeCommand(phone, code, purpose));
    }

    @Override
    public void verifyCode(String email, String code, String purpose) {
        verificationCodeAppService.verifyCode(new VerifyCodeCommand(email, code, purpose));
    }
}
