package com.aieducenter.aieducenteridentity.sso.domain.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class SsoSessionTest {

    private static final Instant CREATED = Instant.parse("2026-07-31T00:00:00Z");
    private static final Instant ABSOLUTE_EXPIRY = Instant.parse("2026-10-29T00:00:00Z"); // +90d

    private final SsoSession session = new SsoSession("sess-1", 101L, "阿福", CREATED, ABSOLUTE_EXPIRY);

    @Test
    void given_now_before_absolute_expiry_when_isExpired_then_false() {
        assertThat(session.isExpired(Instant.parse("2026-08-15T00:00:00Z"))).isFalse();
    }

    @Test
    void given_now_at_absolute_expiry_when_isExpired_then_true() {
        // 到达绝对过期时刻即视为过期（!now.isBefore(absoluteExpiryAt)）
        assertThat(session.isExpired(ABSOLUTE_EXPIRY)).isTrue();
    }

    @Test
    void given_now_after_absolute_expiry_when_isExpired_then_true() {
        assertThat(session.isExpired(Instant.parse("2026-10-30T00:00:00Z"))).isTrue();
    }
}
