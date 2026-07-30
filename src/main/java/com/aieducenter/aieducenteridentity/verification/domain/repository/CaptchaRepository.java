package com.aieducenter.aieducenteridentity.verification.domain.repository;

import com.cartisan.core.stereotype.Port;
import com.cartisan.core.stereotype.PortType;

/**
 * 图形验证码存储端口。
 */
@Port(PortType.REPOSITORY)
public interface CaptchaRepository {

    /**
     * 保存图形验证码。
     *
     * @param captchaId 验证码 ID
     * @param code 验证码文本
     * @param expireSeconds 过期时间（秒）
     */
    void save(String captchaId, String code, long expireSeconds);

    /**
     * 校验并删除图形验证码。
     *
     * @param captchaId 验证码 ID
     * @param code 用户输入的验证码
     * @return 校验是否通过
     */
    boolean verifyAndDelete(String captchaId, String code);
}
