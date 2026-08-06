package com.aieducenter.aieducenteridentity.sso.infrastructure.client;

import java.util.Objects;
import java.util.Optional;

import com.aieducenter.aieducenteridentity.sso.domain.client.SsoClient;

/**
 * SsoClient 本地缓存的值（#32 完整韧性）——缓存值 + 写入时刻（毫秒）。
 *
 * <p>写入时刻用于判「fresh / stale」：fresh 窗口内（{@link RemoteSsoClientRepositoryAdapter#FRESH_TTL_MILLIS}）
 * 命中缓存不打远程；stale（已过 fresh 窗口但仍驻留）在 app-registry 抖动时兜底。
 * {@code value=Optional.empty()} 表示负面缓存（active=false / 404「查不到」）——查得到结论、只是「不可办 SSO」。</p>
 *
 * @param value         解析结果（present=生效 client；empty=负面缓存）
 * @param writtenAtMillis 写入时刻（Clock.millis()），fresh 判定基准
 * @since 0.1.0
 */
public record SsoClientCacheEntry(Optional<SsoClient> value, long writtenAtMillis) {

    public SsoClientCacheEntry {
        Objects.requireNonNull(value, "value 不能为 null（负面缓存用 Optional.empty()）");
    }
}
