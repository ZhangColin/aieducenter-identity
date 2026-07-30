package com.aieducenter.aieducenteridentity.verification.application.dto;

/**
 * 发送验证码响应。
 *
 * @param expireInSeconds 有效期秒数
 * @param resentAfterSeconds 重发间隔秒数
 */
public record SendCodeResponse(
    int expireInSeconds,
    int resentAfterSeconds
) {}
