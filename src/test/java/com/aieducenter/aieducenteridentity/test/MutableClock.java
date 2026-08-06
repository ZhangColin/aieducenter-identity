package com.aieducenter.aieducenteridentity.test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 测试用可控时钟（#32 抖动降级测试基建）——SsoClient 缓存的「过期」判定依赖时钟，
 * 真实 30min TTL 无法在测试里等；用本时钟推进时间，确定性触发 fresh→stale 翻转。
 *
 * <p>测试 profile 以 {@code @Primary} 覆盖生产的 {@code Clock.systemUTC()}，
 * {@link com.aieducenter.aieducenteridentity.test.IdentityIntegrationTestBase} 每测试前 {@link #reset()}。</p>
 */
public final class MutableClock extends Clock {

    private static final long BASE_MILLIS = Instant.parse("2026-01-01T00:00:00Z").toEpochMilli();

    private final AtomicLong millis = new AtomicLong(BASE_MILLIS);

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }

    @Override
    public Instant instant() {
        return Instant.ofEpochMilli(millis.get());
    }

    @Override
    public long millis() {
        return millis.get();
    }

    /** 推进时钟（向后跳），模拟时间流逝使缓存项过期。 */
    public void advance(Duration duration) {
        millis.addAndGet(duration.toMillis());
    }

    /** 复位到基准时刻，保证测试隔离。 */
    public void reset() {
        millis.set(BASE_MILLIS);
    }
}
