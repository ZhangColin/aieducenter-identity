package com.aieducenter.aieducenteridentity.account.application.dto.event;

import java.time.Instant;
import java.util.UUID;

import com.cartisan.event.ApplicationEvent;

/**
 * 用户注册应用事件。
 *
 * <p>注册成功后发布，下游消费（未来如钱包 grant、引导补绑联络方式等）。事件模式从 studio 迁入，
 * 本服务暂无下游监听器——事件发布即可，监听器按实际下游再加。</p>
 */
public record UserRegisteredEvent(
    String eventId,
    Instant occurredAt,
    Long userId,
    String email,
    String phone,
    String nickname
) implements ApplicationEvent {

    public UserRegisteredEvent(Long userId, String email, String phone, String nickname) {
        this(UUID.randomUUID().toString(), Instant.now(), userId, email, phone, nickname);
    }

    @Override
    public String eventType() {
        return "account.user.registered";
    }
}
